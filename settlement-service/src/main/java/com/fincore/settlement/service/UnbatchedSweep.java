package com.fincore.settlement.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fincore.settlement.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class UnbatchedSweep {

    private static final Logger log =
            LoggerFactory.getLogger(UnbatchedSweep.class);

    private final JdbcTemplate jdbc;

    public UnbatchedSweep(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelay = 300_000) // every 5 min
    public void reportUnbatched() {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM settlement_captures c
                WHERE NOT EXISTS (
                    SELECT 1 FROM settlement_batch_items i
                    WHERE i.payment_id = c.payment_id
                )
                AND c.captured_at < CURRENT_TIMESTAMP - INTERVAL '1 day'
                """, Long.class);

        if (count != null && count > 0) {
            log.warn("Late-arrival: {} captures older than 1 day " +
                    "have no batch item — reconciliation adjustments needed",
                    count);
        }
    }
}