package com.fincore.metrics.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class MetricsQueryService {

    private final JdbcTemplate jdbc;

    public MetricsQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Hourly rollup rows in [from, to) — raw material for timeseries charts. */
    public List<Map<String, Object>> paymentTimeseries(Instant from, Instant to) {
        return jdbc.queryForList("""
                SELECT window_start, status, currency, decline_reason,
                       payment_count, volume_minor_units
                FROM payment_metrics_hourly
                WHERE window_start >= ? AND window_start < ?
                ORDER BY window_start, status
                """, Timestamp.from(from), Timestamp.from(to));
    }

    /** Headline aggregates in [from, to) — one row per status. */
    public List<Map<String, Object>> paymentSummary(Instant from, Instant to) {
        return jdbc.queryForList("""
                SELECT status,
                       SUM(payment_count)      AS count,
                       SUM(volume_minor_units) AS volume_minor_units
                FROM payment_metrics_hourly
                WHERE window_start >= ? AND window_start < ?
                GROUP BY status
                """, Timestamp.from(from), Timestamp.from(to));
    }

    /** Top decline reasons in [from, to) — '' means "not a decline". */
    public List<Map<String, Object>> topDeclineReasons(Instant from, Instant to,
                                                       int limit) {
        return jdbc.queryForList("""
                SELECT decline_reason, SUM(payment_count) AS count
                FROM payment_metrics_hourly
                WHERE window_start >= ? AND window_start < ?
                  AND decline_reason <> ''
                GROUP BY decline_reason
                ORDER BY count DESC
                LIMIT ?
                """, Timestamp.from(from), Timestamp.from(to), limit);
    }

    /** Last event the consumers processed — null means "never saw one". */
    public Instant lastProcessedAt() {
        return jdbc.queryForObject(
                "SELECT MAX(processed_at) FROM processed_events",
                Instant.class);
    }

    // ---------------- Request traffic ----------------

    /** Hits per hour split by status class — raw material for traffic charts. */
    public List<Map<String, Object>> requestTimeseries(Instant from, Instant to) {
        return jdbc.queryForList("""
                SELECT window_start, status_class, SUM(hit_count) AS hit_count
                FROM request_metrics_hourly
                WHERE window_start >= ? AND window_start < ?
                GROUP BY window_start, status_class
                ORDER BY window_start
                """, Timestamp.from(from), Timestamp.from(to));
    }

    /** Hit counts grouped by route in [from, to). */
    public List<Map<String, Object>> requestsByRoute(Instant from, Instant to) {
        return jdbc.queryForList("""
                SELECT route, SUM(hit_count) AS hit_count
                FROM request_metrics_hourly
                WHERE window_start >= ? AND window_start < ?
                GROUP BY route
                ORDER BY hit_count DESC
                """, Timestamp.from(from), Timestamp.from(to));
    }

    /**
     * Summed cumulative latency buckets for the whole range — feed into
     * HistogramQuantiles to get p50/p95/p99. Buckets sum correctly across
     * windows; a stored average never could.
     */
    public long[] requestLatencyBuckets(Instant from, Instant to) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT COALESCE(SUM(le_50),0)   AS le_50,
                       COALESCE(SUM(le_100),0)  AS le_100,
                       COALESCE(SUM(le_250),0)  AS le_250,
                       COALESCE(SUM(le_500),0)  AS le_500,
                       COALESCE(SUM(le_1000),0) AS le_1000,
                       COALESCE(SUM(le_2500),0) AS le_2500,
                       COALESCE(SUM(le_inf),0)  AS le_inf
                FROM request_metrics_hourly
                WHERE window_start >= ? AND window_start < ?
                """, Timestamp.from(from), Timestamp.from(to));
        return new long[]{
                ((Number) row.get("le_50")).longValue(),
                ((Number) row.get("le_100")).longValue(),
                ((Number) row.get("le_250")).longValue(),
                ((Number) row.get("le_500")).longValue(),
                ((Number) row.get("le_1000")).longValue(),
                ((Number) row.get("le_2500")).longValue(),
                ((Number) row.get("le_inf")).longValue()};
    }

    /** Fraction of hits that were 5xx in [from, to); 0.0 when no traffic. */
    public double errorRate(Instant from, Instant to) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT COALESCE(SUM(hit_count) FILTER
                           (WHERE status_class = '5xx'), 0) AS errors,
                       COALESCE(SUM(hit_count), 0)           AS total
                FROM request_metrics_hourly
                WHERE window_start >= ? AND window_start < ?
                """, Timestamp.from(from), Timestamp.from(to));
        long errors = ((Number) row.get("errors")).longValue();
        long total = ((Number) row.get("total")).longValue();
        return total == 0 ? 0.0 : (double) errors / total;
    }

    // ---------------- Ledger activity ----------------

    /** Summed ledger rollup for [from, to): txns, entries, debits, credits. */
    public Map<String, Object> ledgerSummary(Instant from, Instant to) {
        return jdbc.queryForMap("""
                SELECT COALESCE(SUM(transaction_count),0) AS transaction_count,
                       COALESCE(SUM(entry_count),0)       AS entry_count,
                       COALESCE(SUM(total_debits),0)      AS total_debits,
                       COALESCE(SUM(total_credits),0)     AS total_credits
                FROM ledger_metrics_hourly
                WHERE window_start >= ? AND window_start < ?
                """, Timestamp.from(from), Timestamp.from(to));
    }

    /** Hourly ledger activity — for the transactions-posted chart. */
    public List<Map<String, Object>> ledgerTimeseries(Instant from, Instant to) {
        return jdbc.queryForList("""
                SELECT window_start, transaction_count, entry_count,
                       total_debits, total_credits
                FROM ledger_metrics_hourly
                WHERE window_start >= ? AND window_start < ?
                ORDER BY window_start
                """, Timestamp.from(from), Timestamp.from(to));
    }
}