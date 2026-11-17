package com.fincore.settlement.service;

import com.fincore.settlement.api.BatchModels.RefundResponse;
import com.fincore.settlement.consumer.CapturedPayment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.task.scheduling.enabled=false"
})
class RefundIT {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:15-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        waitForPort("localhost", postgres.getFirstMappedPort());
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static void waitForPort(String host, int port) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            try (java.net.Socket s = new java.net.Socket(host, port)) {
                return;
            } catch (Exception e) {
                try { Thread.sleep(200); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new IllegalStateException("Postgres not reachable");
    }

    @Autowired private CaptureIngestionService ingestion;
    @Autowired private RefundService refunds;
    @Autowired private SettlementBatchService batches;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReconciliationService reconciliation;

    @Test
    void refundPersistsAndQueuesLedgerEvent() {
        UUID paymentId = capture(10_000);

        RefundResponse r = refunds.refund(
                paymentId, "key-" + paymentId, "customer_request", "admin1");

        assertEquals(paymentId, r.paymentId());
        assertEquals(10_000, r.amountMinor());
        assertEquals("REFUNDED", r.status());
        assertFalse(r.replayed());

        Long events = jdbc.queryForObject("""
                SELECT COUNT(*) FROM settlement_outbox_events
                WHERE event_type = 'payment.refunded'
                  AND aggregate_id = ?""",
                Long.class, paymentId);
        assertEquals(1L, events.longValue());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM refund_audit_events WHERE refund_id = ? AND actor = 'admin1'",
                Long.class, r.id()));
    }

    @Test
    void sameIdempotencyKeyReplaysWithoutSecondEvent() {
        UUID paymentId = capture(10_000);
        String key = "key-" + paymentId;

        RefundResponse first = refunds.refund(
                paymentId, key, "reason", "admin");
        RefundResponse second = refunds.refund(
                paymentId, key, "reason", "admin");

        assertEquals(first.id(), second.id());
        assertTrue(second.replayed());

        Long eventCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM settlement_outbox_events
                WHERE aggregate_id = ?""",
                Long.class, paymentId);
        assertEquals(1L, eventCount.longValue());
    }

    @Test
    void differentKeyOnRefundedPaymentConflicts() {
        UUID paymentId = capture(10_000);
        refunds.refund(paymentId, "key-1", "reason", "admin");

        assertThrows(ResponseStatusException.class,
                () -> refunds.refund(
                        paymentId, "key-2", "reason", "admin"));
    }

    @Test
    void refundBeforeFreezeRollsIntoBatchTotals() {
        String merchant = "merchant-" + UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 1, 10);
        UUID paymentId = UUID.randomUUID();

        ingestion.ingest(new CapturedPayment(
                paymentId, merchant, "order-" + paymentId,
                "capture-" + paymentId, 10_000, "USD",
                Instant.parse("2026-01-10T10:00:00Z")));

        var batch = batches.createOrRefresh(merchant, "USD", day);
        assertEquals(0, batch.refundsMinor());
        assertEquals(9_680, batch.netMinor()); // 10000 - 320 fee

        refunds.refund(paymentId, "key-" + paymentId, "reason", "admin");

        var refreshed = batches.createOrRefresh(merchant, "USD", day);
        assertEquals(10_000, refreshed.refundsMinor());
        assertEquals(-320, refreshed.netMinor()); // merchant owes us the fee
    }

    @Test
    void cannotRefundNonexistentCapture() {
        assertThrows(ResponseStatusException.class,
                () -> refunds.refund(
                        UUID.randomUUID(), "key-x", "reason", "admin"));
    }

    @Test
    void concurrentSameKeyReplaysOneRefund() throws Exception {
        UUID paymentId = capture(10_000);
        String key = "concurrent-" + paymentId;
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return refunds.refund(paymentId, key, "reason", "admin"); });
            var second = executor.submit(() -> { start.await(); return refunds.refund(paymentId, key, "reason", "admin"); });
            start.countDown();
            var a = first.get(15, java.util.concurrent.TimeUnit.SECONDS);
            var b = second.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(a.id(), b.id());
            assertNotEquals(a.replayed(), b.replayed());
        }
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM settlement_outbox_events WHERE aggregate_id = ?", Long.class, paymentId));
    }

    @Test
    void keyCannotBeReusedForAnotherPayment() {
        UUID first = capture(10_000), second = capture(20_000);
        String key = "bound-" + first;
        refunds.refund(first, key, "reason", "admin");
        var error = assertThrows(ResponseStatusException.class,
                () -> refunds.refund(second, key, "reason", "admin"));
        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void refundUpdatesOpenBatchWithoutManualRefresh() {
        String merchant = "merchant-" + UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        ingestion.ingest(new CapturedPayment(paymentId, merchant, "order-" + paymentId,
                "capture-" + paymentId, 10_000, "USD", Instant.parse("2026-01-10T10:00:00Z")));
        var batch = batches.createOrRefresh(merchant, "USD", LocalDate.of(2026, 1, 10));
        refunds.refund(paymentId, "open-" + paymentId, "reason", "admin");
        assertEquals(10_000, batches.get(batch.id()).refundsMinor());
        assertEquals(-320, batches.get(batch.id()).netMinor());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"RECONCILED", "PAID_OUT"})
    void frozenBatchRefundCreatesSeparateAdjustmentWithoutChangingOriginal(String status) {
        String merchant = "merchant-" + UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        ingestion.ingest(new CapturedPayment(paymentId, merchant, "order-" + paymentId,
                "capture-" + paymentId, 10_000, "USD", Instant.parse("2026-01-10T10:00:00Z")));
        var batch = batches.createOrRefresh(merchant, "USD", LocalDate.of(2026, 1, 10));
        jdbc.update("UPDATE settlement_batches SET status = ? WHERE id = ?", status, batch.id());
        var original = batches.get(batch.id());
        var refund = refunds.refund(paymentId, "frozen-" + paymentId, "reason", "admin");
        assertEquals(10_000, refund.amountMinor());
        assertEquals(original, batches.get(batch.id()));
        UUID adjustmentId = jdbc.queryForObject("SELECT id FROM settlement_batches WHERE adjustment_refund_id = ?",
                UUID.class, refund.id());
        var replay = refunds.refund(paymentId, "frozen-" + paymentId, "reason", "admin");
        assertEquals(adjustmentId, refund.adjustmentBatchId());
        assertEquals(adjustmentId, replay.adjustmentBatchId());
        assertTrue(replay.replayed());
        var adjustment = batches.get(adjustmentId);
        assertEquals("REFUND_ADJUSTMENT", adjustment.batchKind());
        assertEquals(batch.id(), adjustment.originalBatchId());
        assertEquals(refund.id(), adjustment.adjustmentRefundId());
        assertEquals(java.util.List.of(adjustment), batches.adjustments(batch.id(), 50, 0));
        assertEquals("OPEN", adjustment.status());
        assertEquals(-10_000, adjustment.netMinor());
        assertEquals(0, adjustment.grossMinor());
        assertEquals(0, adjustment.feesMinor());
        assertEquals(10_000, adjustment.refundsMinor());
        var items = batches.items(adjustmentId, 50, 0);
        assertEquals(1, items.size());
        assertEquals("refund:" + refund.id(), items.getFirst().processorRef());
        assertEquals(-10_000, items.getFirst().amountMinor());
        assertThrows(ResponseStatusException.class, () -> batches.payout(adjustmentId));
        var mismatch = reconciliation.importReport(adjustmentId, "mismatch", java.util.List.of(
                new com.fincore.settlement.reconciliation.ReconciliationModels.SettlementRow(
                        "refund:" + refund.id(), "USD", -9_999, 0)));
        assertTrue(mismatch.breakDetected());
        assertEquals(1, mismatch.mismatchedCount());
        assertEquals("OPEN", batches.get(adjustmentId).status());
        var clean = reconciliation.importReport(adjustmentId, "clean", java.util.List.of(
                new com.fincore.settlement.reconciliation.ReconciliationModels.SettlementRow(
                        "refund:" + refund.id(), "USD", -10_000, 0)));
        assertFalse(clean.breakDetected());
        assertEquals("RECOVERED", batches.payout(adjustmentId).status());
        assertThrows(ResponseStatusException.class, () -> batches.payout(adjustmentId));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM settlement_outbox_events WHERE aggregate_id = ?",
                Long.class, adjustmentId));
        assertEquals(original, batches.get(batch.id()));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM settlement_refunds WHERE payment_id = ?", Long.class, paymentId));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM settlement_outbox_events WHERE aggregate_id = ?", Long.class, paymentId));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void concurrentLateRefundsProduceOneAdjustment(boolean sameKey) throws Exception {
        String merchant = "merchant-" + UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        ingestion.ingest(new CapturedPayment(paymentId, merchant, "order-" + paymentId,
                "capture-" + paymentId, 10_000, "USD", Instant.parse("2026-01-10T10:00:00Z")));
        var batch = batches.createOrRefresh(merchant, "USD", LocalDate.of(2026, 1, 10));
        jdbc.update("UPDATE settlement_batches SET status = 'PAID_OUT' WHERE id = ?", batch.id());
        var original = batches.get(batch.id());
        String key = "late-" + paymentId;
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Object> first = () -> {
                start.await();
                try { return refunds.refund(paymentId, key, "reason", "admin"); }
                catch (ResponseStatusException e) { return e.getStatusCode().value(); }
            };
            java.util.concurrent.Callable<Object> second = () -> {
                start.await();
                try { return refunds.refund(paymentId, sameKey ? key : key + "-other", "reason", "admin"); }
                catch (ResponseStatusException e) { return e.getStatusCode().value(); }
            };
            var a = executor.submit(first);
            var b = executor.submit(second);
            start.countDown();
            var results = java.util.List.of(a.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    b.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(sameKey ? 2 : 1, results.stream().filter(RefundResponse.class::isInstance).count());
            if (sameKey) {
                var firstResult = (RefundResponse) results.getFirst();
                var secondResult = (RefundResponse) results.getLast();
                assertEquals(firstResult.adjustmentBatchId(), secondResult.adjustmentBatchId());
                assertNotEquals(firstResult.replayed(), secondResult.replayed());
            } else assertTrue(results.contains(409));
        }
        assertEquals(1, batches.adjustments(batch.id(), 50, 0).size());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM refund_audit_events WHERE payment_id = ?", Long.class, paymentId));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM settlement_outbox_events WHERE aggregate_id = ?", Long.class, paymentId));
        assertEquals(original, batches.get(batch.id()));
    }

    @Test
    void separateLateRefundsCreateSeparateAdjustmentsInSameMerchantDay() {
        String merchant = "merchant-" + UUID.randomUUID();
        var ids = java.util.List.of(UUID.randomUUID(), UUID.randomUUID());
        for (UUID id : ids) {
            ingestion.ingest(new CapturedPayment(id, merchant, "order-" + id,
                    "capture-" + id, 10_000, "USD", Instant.parse("2026-01-10T10:00:00Z")));
        }
        var batch = batches.createOrRefresh(merchant, "USD", LocalDate.of(2026, 1, 10));
        jdbc.update("UPDATE settlement_batches SET status = 'RECONCILED' WHERE id = ?", batch.id());
        for (UUID id : ids) refunds.refund(id, "late-" + id, "reason", "admin");
        var adjustments = batches.adjustments(batch.id(), 50, 0);
        assertEquals(2, adjustments.size());
        assertEquals(adjustments.getFirst().settlementDate(), adjustments.getLast().settlementDate());
        assertNotEquals(adjustments.getFirst().id(), adjustments.getLast().id());
        assertEquals(batch.id(), batches.createOrRefresh(merchant, "USD", LocalDate.of(2026, 1, 10)).id());
        assertEquals(0, batches.get(batch.id()).refundsMinor());
        assertEquals(1, batches.adjustments(batch.id(), 1, 1).size());
    }

    private UUID capture(long amount) {
        UUID paymentId = UUID.randomUUID();
        ingestion.ingest(new CapturedPayment(
                paymentId, "merchant-1", "order-" + paymentId,
                "capture-" + paymentId, amount, "USD",
                Instant.parse("2026-01-10T10:00:00Z")));
        return paymentId;
    }
}