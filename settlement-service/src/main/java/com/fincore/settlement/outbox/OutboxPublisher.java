package com.fincore.settlement.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OutboxPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(JdbcTemplate jdbc,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.jdbc = jdbc;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void publishPending() {
        List<Map<String, Object>> events = jdbc.queryForList("""
                SELECT id, aggregate_id, event_type, payload
                FROM settlement_outbox_events
                WHERE NOT published
                ORDER BY created_at
                FOR UPDATE SKIP LOCKED
                LIMIT 50
                """);

        for (Map<String, Object> event : events) {
            try {
                kafkaTemplate.send(
                        (String) event.get("event_type"),
                        event.get("aggregate_id").toString(),
                        event.get("payload").toString()
                ).get();

                jdbc.update("""
                        UPDATE settlement_outbox_events
                        SET published = TRUE
                        WHERE id = ?
                        """, event.get("id"));
            } catch (Exception e) {
                log.error("Outbox publish failed for {}, will retry",
                        event.get("id"), e);
                break;
            }
        }
    }
}