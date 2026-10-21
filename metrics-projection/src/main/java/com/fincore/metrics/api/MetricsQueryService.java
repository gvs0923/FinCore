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
}