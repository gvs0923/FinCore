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
    void frozenBatchRefundIsRejectedWithoutFinancialWrites(String status) {
        String merchant = "merchant-" + UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        ingestion.ingest(new CapturedPayment(paymentId, merchant, "order-" + paymentId,
                "capture-" + paymentId, 10_000, "USD", Instant.parse("2026-01-10T10:00:00Z")));
        var batch = batches.createOrRefresh(merchant, "USD", LocalDate.of(2026, 1, 10));
        jdbc.update("UPDATE settlement_batches SET status = ? WHERE id = ?", status, batch.id());
        var error = assertThrows(ResponseStatusException.class,
                () -> refunds.refund(paymentId, "frozen-" + paymentId, "reason", "admin"));
        assertEquals(409, error.getStatusCode().value());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM settlement_refunds WHERE payment_id = ?", Long.class, paymentId));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM settlement_outbox_events WHERE aggregate_id = ?", Long.class, paymentId));
        assertEquals(0, batches.get(batch.id()).refundsMinor());
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