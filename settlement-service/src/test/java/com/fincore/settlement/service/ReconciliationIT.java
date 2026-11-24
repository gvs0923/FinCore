package com.fincore.settlement.service;

import com.fincore.settlement.api.BatchModels.BatchResponse;
import com.fincore.settlement.consumer.CapturedPayment;
import com.fincore.settlement.reconciliation.ReconciliationModels.Classification;
import com.fincore.settlement.reconciliation.ReconciliationModels.RunResponse;
import com.fincore.settlement.reconciliation.ReconciliationModels.SettlementRow;
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
import java.util.List;
import java.util.UUID;

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
class ReconciliationIT {

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

    @Autowired private CaptureIngestionService ingestion;
    @Autowired private SettlementBatchService batches;
    @Autowired private ReconciliationService reconciliation;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void cleanReportMarksBatchReconciled() {
        String merchant = merchant();
        String ref = capture(merchant, 10_000);
        BatchResponse batch = createBatch(merchant);

        RunResponse run = reconciliation.importReport(
                batch.id(), "report-1",
                List.of(row(ref, 10_000, 320)), Actor.system("test"));

        assertEquals(1, run.matchedCount());
        assertEquals(0, run.mismatchedCount());
        assertEquals(
                "RECONCILED",
                batches.get(batch.id()).status());
    }

    @Test
    void mismatchedFeeLeavesBatchOpen() {
        String merchant = merchant();
        String ref = capture(merchant, 10_000);
        BatchResponse batch = createBatch(merchant);

        RunResponse run = reconciliation.importReport(
                batch.id(), "report-1",
                List.of(row(ref, 10_000, 321)), Actor.system("test")); // 1-cent fee break

        assertEquals(1, run.mismatchedCount());
        assertTrue(run.breakDetected());
        assertEquals("OPEN", batches.get(batch.id()).status());
    }

    @Test
    void missingInternalAndExternalBothDetected() {
        String merchant = merchant();
        String ref = capture(merchant, 10_000);
        capture(merchant, 5_000); // not in report → MISSING_EXTERNAL
        BatchResponse batch = createBatch(merchant);

        RunResponse run = reconciliation.importReport(
                batch.id(), "report-1",
                List.of(
                        row(ref, 10_000, 320),
                        row("ghost-capture", 7_000, 233)), Actor.system("test"));

        assertEquals(1, run.matchedCount());
        assertEquals(1, run.missingInternalCount());
        assertEquals(1, run.missingExternalCount());
    }

    @Test
    void reconciledBatchCannotBeReconciledAgainOrRefreshed() {
        String merchant = merchant();
        String ref = capture(merchant, 10_000);
        BatchResponse batch = createBatch(merchant);

        reconciliation.importReport(batch.id(), "report-1",
                List.of(row(ref, 10_000, 320)), Actor.system("test"));

        // Second import → 409
        assertThrows(ResponseStatusException.class,
                () -> reconciliation.importReport(
                        batch.id(), "report-1-dup",
                        List.of(row(ref, 10_000, 320)), Actor.system("test")));

        // Late capture cannot join the frozen batch
        capture(merchant, 5_000);
        BatchResponse after = batches.createOrRefresh(
                merchant, "USD", DAY, Actor.system("test"));
        assertEquals("RECONCILED", after.status());
        assertEquals(1, after.captureCount());
    }

    @Test
    void reportWithForeignCurrencyIsRejected() {
        String merchant = merchant();
        capture(merchant, 10_000);
        BatchResponse batch = createBatch(merchant);

        assertThrows(ResponseStatusException.class,
                () -> reconciliation.importReport(
                        batch.id(), "report-eur",
                        List.of(new SettlementRow(
                                "any", "EUR", 1_000, 30)), Actor.system("test")));
    }

    // --- helpers ---

    private BatchResponse createBatch(String merchant) {
        return batches.createOrRefresh(merchant, "USD", DAY, Actor.system("test"));
    }

    private String capture(String merchant, long amount) {
        UUID paymentId = UUID.randomUUID();
        String processorRef = "capture-" + paymentId;
        ingestion.ingest(new CapturedPayment(
                paymentId, merchant, "order-" + paymentId,
                processorRef, amount, "USD",
                Instant.parse("2026-01-10T10:00:00Z")));
        return processorRef;
    }

    private static SettlementRow row(
            String ref, long amount, long fee) {
        return new SettlementRow(ref, "USD", amount, fee);
    }

    private static String merchant() {
        return "merchant-" + UUID.randomUUID();
    }
}