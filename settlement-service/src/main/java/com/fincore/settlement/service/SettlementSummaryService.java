package com.fincore.settlement.service;

import com.fincore.settlement.api.BatchModels.BatchStatusCount;
import com.fincore.settlement.api.BatchModels.SettlementSummary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Overview figures for the admin dashboard. Reads are plain, non-locking
 * SELECTs over settlement's own low-volume tables (one batch per merchant
 * per day, refunds and runs are admin-driven), never the payment hot path.
 * Settlement is USD-only (enforced by table constraints).
 */
@Service
public class SettlementSummaryService {

    private static final Duration MAX_RANGE = Duration.ofDays(31);

    private final JdbcTemplate jdbc;

    public SettlementSummaryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public SettlementSummary summary(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)
                || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid range: must be <= 31 days");
        }
        Timestamp f = Timestamp.from(from), t = Timestamp.from(to);

        var batches = jdbc.query("""
                SELECT batch_kind, status, COUNT(*) AS n, COALESCE(SUM(net_minor), 0)::BIGINT AS net
                FROM settlement_batches
                GROUP BY batch_kind, status
                ORDER BY batch_kind, status
                """, (rs, n) -> new BatchStatusCount(
                rs.getString("batch_kind"), rs.getString("status"), rs.getLong("n"), rs.getLong("net")));

        long[] refunds = jdbc.queryForObject("""
                SELECT COUNT(*), COALESCE(SUM(amount_minor), 0)::BIGINT
                FROM settlement_refunds
                WHERE status = 'REFUNDED' AND created_at >= ? AND created_at < ?
                """, (rs, n) -> new long[]{rs.getLong(1), rs.getLong(2)}, f, t);

        long[] payouts = jdbc.queryForObject("""
                SELECT COUNT(*), COALESCE(SUM(net_minor), 0)::BIGINT
                FROM settlement_batches
                WHERE status = 'PAID_OUT' AND paid_out_at >= ? AND paid_out_at < ?
                """, (rs, n) -> new long[]{rs.getLong(1), rs.getLong(2)}, f, t);

        long[] runs = jdbc.queryForObject("""
                SELECT COUNT(*), COUNT(*) FILTER (WHERE break_detected)
                FROM reconciliation_runs
                WHERE created_at >= ? AND created_at < ?
                """, (rs, n) -> new long[]{rs.getLong(1), rs.getLong(2)}, f, t);

        return new SettlementSummary("USD", batches,
                refunds[0], refunds[1], payouts[0], payouts[1], runs[0], runs[1]);
    }
}
