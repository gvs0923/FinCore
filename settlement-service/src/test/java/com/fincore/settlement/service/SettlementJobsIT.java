package com.fincore.settlement.service;

import com.fincore.settlement.api.BatchModels.BatchResponse;
import com.fincore.settlement.consumer.CapturedPayment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.task.scheduling.enabled=false",
        "fincore.settlement.jobs.enabled=false"
})
class SettlementJobsIT {

    private static final LocalDate DAY = LocalDate.of(2026, 1, 10);
    private static final LocalDate TODAY = DAY.plusDays(1);

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private CaptureIngestionService ingestion;
    @Autowired private SettlementBatchService batches;
    @Autowired private SettlementJobs jobs;
    @Autowired private SettlementSummaryService summary;
    @Autowired private JdbcTemplate jdbc;

    @TempDir Path inbox;

    @Test
    void scheduledRunBatchesCompletedDaysOnlyAndIsIdempotent() {
        String merchant = merchant();
        capture(merchant, 10_000, "2026-01-10T10:00:00Z");
        capture(merchant, 5_000, "2026-01-09T10:00:00Z");
        capture(merchant, 7_000, "2026-01-11T01:00:00Z"); // today: not complete yet
        capture(merchant, 9_000, "2026-01-02T10:00:00Z"); // outside 7-day lookback

        var created = jobs.createDueBatches(TODAY).stream()
                .filter(b -> merchant.equals(b.merchantId())).toList();

        assertEquals(2, created.size());
        assertEquals(15_000, created.stream().mapToLong(BatchResponse::grossMinor).sum());
        assertEquals(2L, batchCount(merchant));

        assertTrue(jobs.createDueBatches(TODAY).stream().noneMatch(b -> merchant.equals(b.merchantId())));
        assertEquals(2L, batchCount(merchant));
    }

    @Test
    void cleanInboxReportReconcilesAndMovesToProcessed() throws Exception {
        String merchant = merchant();
        String ref = capture(merchant, 10_000, "2026-01-10T10:00:00Z");
        String name = reportName(merchant);
        Files.writeString(inbox.resolve(name),
                "processorRef,currency,amountMinor,feeMinor\n" + ref + ",USD,10000,320\n");

        var runs = jobs.importInbox(inbox);

        assertEquals(1, runs.size());
        assertFalse(runs.getFirst().breakDetected());
        assertEquals(name, runs.getFirst().reportRef());
        assertEquals("RECONCILED", batches.get(runs.getFirst().batchId()).status());
        assertTrue(Files.exists(inbox.resolve("processed").resolve(name)));
        assertFalse(Files.exists(inbox.resolve(name)));
    }

    @Test
    void breakIsRecordedAndBatchStaysOpen() throws Exception {
        String merchant = merchant();
        String ref = capture(merchant, 10_000, "2026-01-10T10:00:00Z");
        String name = reportName(merchant);
        Files.writeString(inbox.resolve(name),
                "processorRef,currency,amountMinor,feeMinor\n" + ref + ",USD,10000,321\n");

        var runs = jobs.importInbox(inbox);

        assertTrue(runs.getFirst().breakDetected());
        assertEquals("OPEN", batches.get(runs.getFirst().batchId()).status());
        assertTrue(Files.exists(inbox.resolve("processed").resolve(name)));
    }

    @Test
    void invalidReportsMoveToFailedWithoutARun() throws Exception {
        String merchant = merchant();
        capture(merchant, 10_000, "2026-01-10T10:00:00Z");
        Files.writeString(inbox.resolve(reportName(merchant)), "wrong,header\nx,USD,1,0\n");
        Files.writeString(inbox.resolve("not-a-report.csv"), "processorRef,currency,amountMinor,feeMinor\n");
        Files.writeString(inbox.resolve("ignored.txt"), "left alone");

        assertTrue(jobs.importInbox(inbox).isEmpty());

        assertTrue(Files.exists(inbox.resolve("failed").resolve(reportName(merchant))));
        assertTrue(Files.exists(inbox.resolve("failed").resolve("not-a-report.csv")));
        assertTrue(Files.exists(inbox.resolve("ignored.txt")));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT COUNT(*) FROM reconciliation_runs r
                JOIN settlement_batches b ON b.id = r.batch_id WHERE b.merchant_id = ?
                """, Long.class, merchant));
    }

    @Test
    void reportForAlreadyReconciledBatchIsRejected() throws Exception {
        String merchant = merchant();
        String ref = capture(merchant, 10_000, "2026-01-10T10:00:00Z");
        String csv = "processorRef,currency,amountMinor,feeMinor\n" + ref + ",USD,10000,320\n";
        Files.writeString(inbox.resolve(reportName(merchant)), csv);
        assertEquals(1, jobs.importInbox(inbox).size());

        Files.writeString(inbox.resolve(reportName(merchant)), csv);
        assertTrue(jobs.importInbox(inbox).isEmpty());
        assertTrue(Files.exists(inbox.resolve("failed").resolve(reportName(merchant))));
    }

    @Test
    void summaryCountsBatchStatesAndWindowedActivity() throws Exception {
        Instant from = Instant.now().minus(Duration.ofMinutes(1));
        var before = summary.summary(from, Instant.now().plusSeconds(60));

        String merchant = merchant();
        String ref = capture(merchant, 10_000, "2026-01-10T10:00:00Z");
        Files.writeString(inbox.resolve(reportName(merchant)),
                "processorRef,currency,amountMinor,feeMinor\n" + ref + ",USD,10000,320\n");
        UUID batchId = jobs.importInbox(inbox).getFirst().batchId();
        batches.payout(batchId);

        var after = summary.summary(from, Instant.now().plusSeconds(60));

        assertEquals("USD", after.currency());
        assertEquals(before.reconciliationRuns() + 1, after.reconciliationRuns());
        assertEquals(before.reconciliationBreaks(), after.reconciliationBreaks());
        assertEquals(before.payoutCount() + 1, after.payoutCount());
        assertEquals(before.payoutMinor() + 9_680, after.payoutMinor());
        assertTrue(after.batches().stream().anyMatch(b ->
                "DAILY".equals(b.batchKind()) && "PAID_OUT".equals(b.status()) && b.count() >= 1));
    }

    private long batchCount(String merchant) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM settlement_batches WHERE merchant_id = ?", Long.class, merchant);
    }

    private static String reportName(String merchant) {
        return "settlement_" + merchant + "_USD_" + DAY + ".csv";
    }

    private String capture(String merchant, long amount, String capturedAt) {
        UUID paymentId = UUID.randomUUID();
        String ref = "capture-" + paymentId;
        ingestion.ingest(new CapturedPayment(paymentId, merchant, "order-" + paymentId, ref,
                amount, "USD", Instant.parse(capturedAt)));
        return ref;
    }

    private static String merchant() {
        return "merchant_" + UUID.randomUUID();
    }
}
