package com.fincore.settlement.service;

import com.fincore.settlement.api.BatchModels.BatchResponse;
import com.fincore.settlement.audit.Actor;
import com.fincore.settlement.reconciliation.ProcessorReportCsv;
import com.fincore.settlement.reconciliation.ReconciliationModels.RunResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The work behind the scheduled jobs, kept separate from the triggers so
 * tests can drive it directly. Both jobs reuse the same service methods
 * as the admin actions, so they take the same advisory locks and are safe
 * to run on several instances at once.
 */
@Service
public class SettlementJobs {

    private static final Logger log = LoggerFactory.getLogger(SettlementJobs.class);

    // Catch-up window: a missed run (service down at the cron time) is
    // picked up by the next one.
    static final int LOOKBACK_DAYS = 7;

    // settlement_{merchantId}_{currency}_{yyyy-MM-dd}.csv
    private static final Pattern REPORT_NAME =
            Pattern.compile("settlement_(.+)_([A-Z]{3})_(\\d{4}-\\d{2}-\\d{2})\\.csv");

    private final JdbcTemplate jdbc;
    private final SettlementBatchService batchService;
    private final ReconciliationService reconciliationService;

    public SettlementJobs(JdbcTemplate jdbc,
                          SettlementBatchService batchService,
                          ReconciliationService reconciliationService) {
        this.jdbc = jdbc;
        this.batchService = batchService;
        this.reconciliationService = reconciliationService;
    }

    /** Creates or refreshes the DAILY batch of every completed UTC day in the lookback window that has unbatched captures. */
    public List<BatchResponse> createDueBatches(LocalDate today) {
        List<Object[]> scopes = jdbc.query("""
                SELECT DISTINCT c.merchant_id, c.currency,
                       (c.captured_at AT TIME ZONE 'UTC')::date AS settlement_date
                FROM settlement_captures c
                WHERE c.captured_at >= ? AND c.captured_at < ?
                  AND NOT EXISTS (
                      SELECT 1 FROM settlement_batch_items i
                      WHERE i.payment_id = c.payment_id)
                ORDER BY settlement_date, c.merchant_id, c.currency
                """,
                (rs, n) -> new Object[]{rs.getString(1), rs.getString(2), rs.getDate(3).toLocalDate()},
                Timestamp.from(today.minusDays(LOOKBACK_DAYS).atStartOfDay(ZoneOffset.UTC).toInstant()),
                Timestamp.from(today.atStartOfDay(ZoneOffset.UTC).toInstant()));

        List<BatchResponse> batches = new ArrayList<>();
        for (Object[] s : scopes) {
            try {
                BatchResponse batch = batchService.createOrRefresh((String) s[0], (String) s[1],
                        (LocalDate) s[2], Actor.system("daily-batch-job"));
                if ("OPEN".equals(batch.status())) {
                    batches.add(batch);
                }
            } catch (RuntimeException e) {
                log.warn("Scheduled batch for {} {} {} failed: {}", s[0], s[1], s[2], reason(e));
            }
        }
        log.info("Scheduled batch run: {} open batch(es) created or refreshed", batches.size());
        return batches;
    }

    /**
     * Imports every processor report in the inbox. A file is claimed by an
     * atomic move into processing/ (so only one instance handles it), then
     * moved to processed/ once a run is recorded, or to failed/ otherwise.
     * Producers must write elsewhere and rename into the inbox.
     */
    public List<RunResponse> importInbox(Path inbox) throws IOException {
        Path processing = Files.createDirectories(inbox.resolve("processing"));
        Path processed = Files.createDirectories(inbox.resolve("processed"));
        Path failed = Files.createDirectories(inbox.resolve("failed"));

        List<Path> files;
        try (Stream<Path> list = Files.list(inbox)) {
            files = list.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".csv"))
                    .sorted()
                    .toList();
        }

        List<RunResponse> runs = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            Path claimed = processing.resolve(name);
            try {
                Files.move(file, claimed, StandardCopyOption.ATOMIC_MOVE);
            } catch (NoSuchFileException e) {
                continue; // another instance claimed it
            }
            try {
                RunResponse run = importReport(name, claimed);
                Files.move(claimed, processed.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                log.info("Imported processor report {}: run {} break={}", name, run.id(), run.breakDetected());
                runs.add(run);
            } catch (RuntimeException | IOException e) {
                Files.move(claimed, failed.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                log.warn("Processor report {} rejected: {}", name, reason(e));
            }
        }
        return runs;
    }

    private RunResponse importReport(String name, Path file) throws IOException {
        Matcher m = REPORT_NAME.matcher(name);
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "File name must be settlement_{merchantId}_{currency}_{yyyy-MM-dd}.csv");
        }
        if (Files.size(file) > ProcessorReportCsv.MAX_CHARS) {
            throw new IllegalArgumentException("Report exceeds 2 MB");
        }
        var rows = ProcessorReportCsv.parse(Files.readString(file, StandardCharsets.UTF_8));
        BatchResponse batch = batchService.createOrRefresh(m.group(1), m.group(2),
                LocalDate.parse(m.group(3)), Actor.system("report-inbox"));
        return reconciliationService.importReport(batch.id(), name, rows, Actor.system("report-inbox"));
    }

    private static String reason(Exception e) {
        return e instanceof ResponseStatusException rse && rse.getReason() != null
                ? rse.getReason() : e.getMessage();
    }
}
