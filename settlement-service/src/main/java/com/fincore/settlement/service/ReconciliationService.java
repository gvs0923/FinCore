package com.fincore.settlement.service;

import com.fincore.settlement.reconciliation.ReconciliationEngine;
import com.fincore.settlement.reconciliation.ReconciliationModels.Classification;
import com.fincore.settlement.reconciliation.ReconciliationModels.Comparison;
import com.fincore.settlement.reconciliation.ReconciliationModels.RunResponse;
import com.fincore.settlement.reconciliation.ReconciliationModels.SettlementRow;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ReconciliationService {

    private final JdbcTemplate jdbc;
    private final ReconciliationEngine engine;

    public ReconciliationService(
            JdbcTemplate jdbc,
            ReconciliationEngine engine) {
        this.jdbc = jdbc;
        this.engine = engine;
    }

    @Transactional
    public RunResponse importReport(
            UUID batchId,
            String reportRef,
            List<SettlementRow> externalRows) {

        // Step 1: lock the batch row — nothing can refresh its items
        // while this transaction runs.
        var batch = jdbc.query("""
                SELECT status, currency
                FROM settlement_batches
                WHERE id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new String[]{
                        rs.getString("status"),
                        rs.getString("currency")},
                batchId);

        if (batch.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Settlement batch not found");
        }
        if (!"OPEN".equals(batch.getFirst()[0])) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Batch is already reconciled or paid out");
        }
        String batchCurrency = batch.getFirst()[1];

        // Step 2: internal side = persisted batch items.
        List<SettlementRow> internalRows = jdbc.query("""
                SELECT processor_ref, amount_minor, fee_minor
                FROM settlement_batch_items
                WHERE batch_id = ?
                ORDER BY processor_ref
                """,
                (rs, rowNum) -> new SettlementRow(
                        rs.getString("processor_ref"),
                        batchCurrency,
                        rs.getLong("amount_minor"),
                        rs.getLong("fee_minor")),
                batchId);

        // Step 3: the report must declare only this batch's currency.
        boolean foreignCurrency = externalRows.stream()
                .anyMatch(r -> !batchCurrency.equals(r.currency()));
        if (foreignCurrency) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Report contains rows outside batch currency "
                            + batchCurrency);
        }

        // Step 4: compare.
        List<Comparison> comparisons =
                engine.compare(internalRows, externalRows);

        // Step 5: persist run + items in the same transaction.
        int matched = 0, mismatched = 0,
                missingInternal = 0, missingExternal = 0;

        for (Comparison c : comparisons) {
            switch (c.classification()) {
                case MATCHED -> matched++;
                case MISMATCHED -> mismatched++;
                case MISSING_INTERNAL -> missingInternal++;
                case MISSING_EXTERNAL -> missingExternal++;
            }
        }
        boolean breakDetected = mismatched > 0
                || missingInternal > 0 || missingExternal > 0;

        UUID runId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO reconciliation_runs (
                    id, batch_id, report_ref, item_count,
                    matched_count, mismatched_count,
                    missing_internal_count, missing_external_count,
                    break_detected
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                runId, batchId, reportRef, comparisons.size(),
                matched, mismatched,
                missingInternal, missingExternal, breakDetected);

        for (Comparison c : comparisons) {
            jdbc.update("""
                    INSERT INTO reconciliation_items (
                        id, run_id, processor_ref, classification,
                        internal_amount_minor, internal_fee_minor,
                        internal_currency,
                        external_amount_minor, external_fee_minor,
                        external_currency
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(), runId,
                    c.processorRef(), c.classification().name(),
                    c.internalRow() == null ? null
                            : c.internalRow().amountMinor(),
                    c.internalRow() == null ? null
                            : c.internalRow().feeMinor(),
                    c.internalRow() == null ? null
                            : c.internalRow().currency(),
                    c.externalRow() == null ? null
                            : c.externalRow().amountMinor(),
                    c.externalRow() == null ? null
                            : c.externalRow().feeMinor(),
                    c.externalRow() == null ? null
                            : c.externalRow().currency());
        }

        // Step 6: a clean run freezes the batch as RECONCILED.
        // Breaks leave it OPEN for investigation — the report rows
        // are the audit trail.
        if (!breakDetected) {
            jdbc.update("""
                    UPDATE settlement_batches
                    SET status = 'RECONCILED',
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'OPEN'
                    """,
                    batchId);
        }

        return new RunResponse(runId, batchId, reportRef,
                comparisons.size(), matched, mismatched,
                missingInternal, missingExternal, breakDetected);
    }
}