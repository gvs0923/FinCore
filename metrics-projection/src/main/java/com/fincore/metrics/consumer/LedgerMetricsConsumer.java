package com.fincore.metrics.consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
public class LedgerMetricsConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(LedgerMetricsConsumer.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public LedgerMetricsConsumer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.objectMapper = new ObjectMapper();
    }

    @KafkaListener(topics = "ledger.transaction.posted",
                   groupId = "metrics-projection")
    @Transactional
    public void onLedgerEvent(String message,
                              @Header(KafkaHeaders.RECEIVED_KEY) String key,
                              @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        try {
            JsonNode event = objectMapper.readTree(message);

            // Idempotency: deduplicate by event key
            UUID eventKey = UUID.nameUUIDFromBytes(
                    (key + ":ledger.transaction.posted").getBytes(StandardCharsets.UTF_8));
            try {
                jdbc.update("INSERT INTO processed_events (event_id) VALUES (?)", eventKey);
            } catch (DuplicateKeyException e) {
                return;
            }

            int entryCount = event.get("entryCount").asInt();
            long totalDebits = event.get("totalDebits").asLong();
            long totalCredits = event.get("totalCredits").asLong();

            Instant window = Instant.ofEpochMilli(timestamp)
                    .truncatedTo(ChronoUnit.HOURS);

            jdbc.update("""
                    INSERT INTO ledger_metrics_hourly
                        (window_start, transaction_count, entry_count,
                         total_debits, total_credits)
                    VALUES (?, 1, ?, ?, ?)
                    ON CONFLICT (window_start)
                    DO UPDATE SET
                        transaction_count = ledger_metrics_hourly.transaction_count + 1,
                        entry_count = ledger_metrics_hourly.entry_count + ?,
                        total_debits = ledger_metrics_hourly.total_debits + ?,
                        total_credits = ledger_metrics_hourly.total_credits + ?
                    """,
                    Timestamp.from(window), entryCount, totalDebits, totalCredits,
                    entryCount, totalDebits, totalCredits);
        } catch (Exception e) {
            log.error("Failed to process ledger event: {}", message, e);
            throw new RuntimeException(e);
        }
    }
}