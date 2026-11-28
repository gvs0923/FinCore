package com.fincore.settlement.service;

import com.fincore.settlement.reconciliation.AnomalyAggregates;
import com.fincore.settlement.reconciliation.AnomalyAggregates.Delta;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ReconciliationAnomalyService {

    // Deltas are processor minus internal, and only over same-currency rows:
    // subtracting amounts in different currencies is meaningless.
    private static final String COUNTS = """
            SELECT COUNT(*),
                   COUNT(*) FILTER (WHERE classification = 'MATCHED'),
                   COUNT(*) FILTER (WHERE classification = 'MISMATCHED'),
                   COUNT(*) FILTER (WHERE classification = 'MISSING_INTERNAL'),
                   COUNT(*) FILTER (WHERE classification = 'MISSING_EXTERNAL'),
                   COUNT(*) FILTER (WHERE classification = 'MISMATCHED'
                       AND internal_amount_minor <> external_amount_minor AND internal_fee_minor = external_fee_minor),
                   COUNT(*) FILTER (WHERE classification = 'MISMATCHED'
                       AND internal_amount_minor = external_amount_minor AND internal_fee_minor <> external_fee_minor),
                   COUNT(*) FILTER (WHERE classification = 'MISMATCHED'
                       AND internal_amount_minor <> external_amount_minor AND internal_fee_minor <> external_fee_minor),
                   COUNT(*) FILTER (WHERE classification = 'MISMATCHED' AND internal_currency <> external_currency),
                   COALESCE(SUM(external_amount_minor - internal_amount_minor) FILTER (WHERE classification = 'MISMATCHED'
                       AND internal_currency = external_currency), 0)::BIGINT,
                   COALESCE(SUM(external_fee_minor - internal_fee_minor) FILTER (WHERE classification = 'MISMATCHED'
                       AND internal_currency = external_currency), 0)::BIGINT,
                   COALESCE(SUM(external_amount_minor) FILTER (WHERE classification = 'MISSING_INTERNAL'), 0)::BIGINT,
                   COALESCE(SUM(internal_amount_minor) FILTER (WHERE classification = 'MISSING_EXTERNAL'), 0)::BIGINT
            FROM reconciliation_items
            WHERE run_id = ?
            """;

    private final JdbcTemplate jdbc;

    public ReconciliationAnomalyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public AnomalyAggregates aggregates(UUID runId) {
        List<Object[]> run = jdbc.query("""
                SELECT r.batch_id, r.report_ref, b.currency
                FROM reconciliation_runs r JOIN settlement_batches b ON b.id = r.batch_id
                WHERE r.id = ?
                """, (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)}, runId);
        if (run.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconciliation run not found");
        }
        long[] c = jdbc.queryForObject(COUNTS, (rs, n) -> {
            long[] values = new long[13];
            for (int i = 0; i < values.length; i++) {
                values[i] = rs.getLong(i + 1);
            }
            return values;
        }, runId);
        List<Delta> deltas = jdbc.query("""
                SELECT processor_ref,
                       external_amount_minor - internal_amount_minor,
                       external_fee_minor - internal_fee_minor
                FROM reconciliation_items
                WHERE run_id = ? AND classification = 'MISMATCHED' AND internal_currency = external_currency
                ORDER BY ABS(external_amount_minor - internal_amount_minor)
                       + ABS(external_fee_minor - internal_fee_minor) DESC, processor_ref
                LIMIT 5
                """, (rs, n) -> new Delta(rs.getString(1), rs.getLong(2), rs.getLong(3)), runId);
        Object[] r = run.get(0);
        return new AnomalyAggregates(runId, (UUID) r[0], (String) r[1], (String) r[2],
                c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9], c[10], c[11], c[12], deltas);
    }
}
