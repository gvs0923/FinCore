package com.fincore.metrics.rollup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Rolls hourly rollup rows into daily rows once they pass the retention
 * cutoff, then deletes the hourly rows. Hourly data is kept for
 * HOURLY_RETENTION_DAYS days; anything older lives on as daily data.
 * Rollups are projections — deleting them is safe because they can be
 * rebuilt from the event log.
 */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);
    private static final int HOURLY_RETENTION_DAYS = 7;

    private final JdbcTemplate jdbc;

    public RetentionJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 30 * * * *")  // 30 min past each hour; use daily cron in prod
    @Transactional
    public void rollUp() {
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC)
                .minusDays(HOURLY_RETENTION_DAYS);

        int payments = jdbc.update("""
                INSERT INTO payment_metrics_daily
                    (day, status, currency, decline_reason,
                     payment_count, volume_minor_units)
                SELECT window_start::date, status, currency, decline_reason,
                       SUM(payment_count), SUM(volume_minor_units)
                FROM payment_metrics_hourly
                WHERE window_start::date < ?
                GROUP BY window_start::date, status, currency, decline_reason
                ON CONFLICT (day, status, currency, decline_reason)
                DO UPDATE SET
                    payment_count      = payment_metrics_daily.payment_count
                                         + EXCLUDED.payment_count,
                    volume_minor_units = payment_metrics_daily.volume_minor_units
                                         + EXCLUDED.volume_minor_units
                """, cutoff);

        jdbc.update("""
                INSERT INTO ledger_metrics_daily
                    (day, transaction_count, entry_count, total_debits, total_credits)
                SELECT window_start::date,
                       SUM(transaction_count), SUM(entry_count),
                       SUM(total_debits), SUM(total_credits)
                FROM ledger_metrics_hourly
                WHERE window_start::date < ?
                GROUP BY window_start::date
                ON CONFLICT (day) DO UPDATE SET
                    transaction_count = ledger_metrics_daily.transaction_count
                                        + EXCLUDED.transaction_count,
                    entry_count       = ledger_metrics_daily.entry_count
                                        + EXCLUDED.entry_count,
                    total_debits      = ledger_metrics_daily.total_debits
                                        + EXCLUDED.total_debits,
                    total_credits     = ledger_metrics_daily.total_credits
                                        + EXCLUDED.total_credits
                """, cutoff);

        jdbc.update("""
                INSERT INTO request_metrics_daily
                    (day, route, status_class, hit_count,
                     le_50, le_100, le_250, le_500, le_1000, le_2500, le_inf)
                SELECT window_start::date, route, status_class,
                       SUM(hit_count), SUM(le_50), SUM(le_100), SUM(le_250),
                       SUM(le_500), SUM(le_1000), SUM(le_2500), SUM(le_inf)
                FROM request_metrics_hourly
                WHERE window_start::date < ?
                GROUP BY window_start::date, route, status_class
                ON CONFLICT (day, route, status_class) DO UPDATE SET
                    hit_count = request_metrics_daily.hit_count + EXCLUDED.hit_count,
                    le_50   = request_metrics_daily.le_50   + EXCLUDED.le_50,
                    le_100  = request_metrics_daily.le_100  + EXCLUDED.le_100,
                    le_250  = request_metrics_daily.le_250  + EXCLUDED.le_250,
                    le_500  = request_metrics_daily.le_500  + EXCLUDED.le_500,
                    le_1000 = request_metrics_daily.le_1000 + EXCLUDED.le_1000,
                    le_2500 = request_metrics_daily.le_2500 + EXCLUDED.le_2500,
                    le_inf  = request_metrics_daily.le_inf  + EXCLUDED.le_inf
                """, cutoff);

        int deleted = jdbc.update("""
                DELETE FROM payment_metrics_hourly WHERE window_start::date < ?
                """, cutoff);
        jdbc.update("DELETE FROM ledger_metrics_hourly WHERE window_start::date < ?", cutoff);
        jdbc.update("DELETE FROM request_metrics_hourly WHERE window_start::date < ?", cutoff);

        log.info("Retention rollup done: cutoff={}, payment rows merged={}, hourly rows deleted={}",
                cutoff, payments, deleted);
    }
}