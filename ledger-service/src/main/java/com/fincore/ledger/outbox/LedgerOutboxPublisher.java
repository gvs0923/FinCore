package com.fincore.ledger.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class LedgerOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(LedgerOutboxPublisher.class);

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;

    public LedgerOutboxPublisher(JdbcTemplate jdbc,
                                  KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, event_type, aggregate_id, payload::text
                FROM ledger_outbox_events
                WHERE published_at IS NULL
                ORDER BY created_at
                LIMIT 100
                """);

        for (Map<String, Object> row : rows) {
            String topic = (String) row.get("event_type");
            String key = (String) row.get("aggregate_id");
            String payload = (String) row.get("payload");
            kafka.send(topic, key, payload);

            jdbc.update(
                    "UPDATE ledger_outbox_events SET published_at = ? WHERE id = ?",
                    Timestamp.from(Instant.now()), row.get("id"));
        }
        if (!rows.isEmpty()) {
            log.debug("Published {} ledger outbox events", rows.size());
        }
    }
}