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

    private UUID capture(long amount) {
        UUID paymentId = UUID.randomUUID();
        ingestion.ingest(new CapturedPayment(
                paymentId, "merchant-1", "order-" + paymentId,
                "capture-" + paymentId, amount, "USD",
                Instant.parse("2026-01-10T10:00:00Z")));
        return paymentId;
    }
}