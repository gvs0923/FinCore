package com.fincore.settlement.service;

import com.fincore.settlement.api.BatchModels.BatchResponse;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.fincore.settlement.audit.Actor;

@Testcontainers
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.task.scheduling.enabled=false",
        "fincore.settlement.jobs.enabled=false"
})
class SettlementBatchIT {

    private static final LocalDate DAY = LocalDate.of(2026, 1, 10);

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
            try (java.net.Socket ignored = new java.net.Socket(host, port)) {
                return;
            } catch (Exception e) {
                try { Thread.sleep(200); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new IllegalStateException(
                "Postgres port not reachable in time");
    }

    @Autowired
    private CaptureIngestionService ingestionService;

    @Autowired
    private SettlementBatchService batchService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void calculatesTotalsAndPersistsFeeSnapshots() {
        String merchant = merchant();

        capture(merchant, 10_000, "2026-01-10T10:00:00Z");
        capture(merchant, 5_000, "2026-01-10T11:00:00Z");

        BatchResponse batch = batchService.createOrRefresh(
                merchant, "USD", DAY, Actor.system("test"));

        assertEquals("OPEN", batch.status());
        assertEquals(2, batch.captureCount());
        assertEquals(15_000, batch.grossMinor());
        assertEquals(495, batch.feesMinor());
        assertEquals(0, batch.refundsMinor());
        assertEquals(14_505, batch.netMinor());

        var items = batchService.items(batch.id(), 100, 0);

        assertEquals(2, items.size());
        assertTrue(items.stream().allMatch(item ->
                "USD_2_9_PERCENT_PLUS_30_V1".equals(
                        item.feePolicyVersion())));
    }

    @Test
    void repeatedCreationDoesNotDuplicateItems() {
        String merchant = merchant();
        capture(merchant, 10_000, "2026-01-10T10:00:00Z");

        BatchResponse first = batchService.createOrRefresh(
                merchant, "USD", DAY, Actor.system("test"));

        BatchResponse second = batchService.createOrRefresh(
                merchant, "USD", DAY, Actor.system("test"));

        assertEquals(first.id(), second.id());
        assertEquals(1, second.captureCount());
        assertEquals(10_000, second.grossMinor());
        assertEquals(320, second.feesMinor());
        assertEquals(1, batchService.items(second.id(), 100, 0).size());
    }

    @Test
    void refreshIncludesLateArrivalWhileBatchIsOpen() {
        String merchant = merchant();
        capture(merchant, 10_000, "2026-01-10T10:00:00Z");

        BatchResponse first = batchService.createOrRefresh(
                merchant, "USD", DAY, Actor.system("test"));

        capture(merchant, 5_000, "2026-01-10T11:00:00Z");

        BatchResponse refreshed = batchService.createOrRefresh(
                merchant, "USD", DAY, Actor.system("test"));

        assertEquals(first.id(), refreshed.id());
        assertEquals(2, refreshed.captureCount());
        assertEquals(15_000, refreshed.grossMinor());
        assertEquals(495, refreshed.feesMinor());
    }

    @Test
    void usesInclusiveStartAndExclusiveEnd() {
        String merchant = merchant();

        capture(merchant, 1_000, "2026-01-09T23:59:59Z");
        capture(merchant, 2_000, "2026-01-10T00:00:00Z");
        capture(merchant, 3_000, "2026-01-10T23:59:59Z");
        capture(merchant, 4_000, "2026-01-11T00:00:00Z");

        BatchResponse batch = batchService.createOrRefresh(
                merchant, "USD", DAY, Actor.system("test"));

        assertEquals(2, batch.captureCount());
        assertEquals(5_000, batch.grossMinor());
    }

    @Test
    void doesNotMixMerchants() {
        String firstMerchant = merchant();
        String secondMerchant = merchant();

        capture(firstMerchant, 10_000, "2026-01-10T10:00:00Z");
        capture(secondMerchant, 20_000, "2026-01-10T10:00:00Z");

        BatchResponse batch = batchService.createOrRefresh(
                firstMerchant, "USD", DAY, Actor.system("test"));

        assertEquals(1, batch.captureCount());
        assertEquals(10_000, batch.grossMinor());
    }

        @Test
    void payoutRequiresReconciledAndFlipsStatus() {
        String merchant = merchant();
        capture(merchant, 10_000, "2026-01-10T10:00:00Z");
        BatchResponse batch = batchService.createOrRefresh(
                merchant, "USD", DAY, Actor.system("test"));

        // OPEN batch cannot pay out
        assertThrows(ResponseStatusException.class,
                () -> batchService.payout(batch.id(), Actor.system("test")));

        // Simulate a clean reconciliation directly (real flow tested
        // in ReconciliationIT — here we test payout's guard)
        jdbc.update("""
                UPDATE settlement_batches SET status = 'RECONCILED'
                WHERE id = ?""", batch.id());

        BatchResponse paid = batchService.payout(batch.id(), Actor.system("test"));
        assertEquals("PAID_OUT", paid.status());

        // Second payout → 409 (idempotent by state guard)
        assertThrows(ResponseStatusException.class,
                () -> batchService.payout(batch.id(), Actor.system("test")));

        // Exactly one outbox event queued for the ledger
        Long events = jdbc.queryForObject("""
                SELECT COUNT(*) FROM settlement_outbox_events
                WHERE aggregate_id = ?""",
                Long.class, batch.id());
        assertEquals(1L, events.longValue());
    }

    @Test
    void concurrentCreationReturnsOneBatchAndOneItem() throws Exception {
        String merchant = merchant();
        capture(merchant, 10_000, "2026-01-10T10:00:00Z");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start signal timed out");
                }
                return batchService.createOrRefresh(
                        merchant, "USD", DAY, Actor.system("test"));
            });

            var second = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start signal timed out");
                }
                return batchService.createOrRefresh(
                        merchant, "USD", DAY, Actor.system("test"));
            });

            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }

            BatchResponse firstResult = first.get(30, TimeUnit.SECONDS);
            BatchResponse secondResult = second.get(30, TimeUnit.SECONDS);

            assertEquals(firstResult.id(), secondResult.id());
            assertEquals(
                    1,
                    batchService.get(firstResult.id()).captureCount());

            Long batchCount = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM settlement_batches
                    WHERE merchant_id = ?
                      AND currency = 'USD'
                      AND settlement_date = ?
                    """,
                    Long.class,
                    merchant,
                    java.sql.Date.valueOf(DAY));

            assertEquals(1L, batchCount.longValue());
            assertEquals(
                    1,
                    batchService.items(firstResult.id(), 100, 0).size());
        }
    }

    @Test
    void rejectsFutureSettlementDate() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> batchService.createOrRefresh(
                        merchant(),
                        "USD",
                        LocalDate.of(9999, 1, 1), Actor.system("test")));

        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void rejectsUnsupportedCurrency() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> batchService.createOrRefresh(
                        merchant(), "EUR", DAY, Actor.system("test")));

        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void rejectsInvalidPagination() {
        BatchResponse batch = batchService.createOrRefresh(
                merchant(), "USD", DAY, Actor.system("test"));

        assertThrows(
                ResponseStatusException.class,
                () -> batchService.items(batch.id(), 101, 0));

        assertThrows(
                ResponseStatusException.class,
                () -> batchService.items(batch.id(), 50, -1));
    }

    private void capture(
            String merchant,
            long amount,
            String capturedAt) {
        UUID paymentId = UUID.randomUUID();

        ingestionService.ingest(new CapturedPayment(
                paymentId,
                merchant,
                "order-" + paymentId,
                "capture-" + paymentId,
                amount,
                "USD",
                Instant.parse(capturedAt)));
    }

    private static String merchant() {
        return "merchant-" + UUID.randomUUID();
    }
}