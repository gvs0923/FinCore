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
public class PaymentMetricsConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentMetricsConsumer.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PaymentMetricsConsumer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
   
        this.objectMapper = new ObjectMapper();
    }

 
    // [a-z_]+ excludes "-": a looser "payment\\..*" also matched our own
    // dead-letter topics (payment.captured-dlt), so a failing message was
    // re-consumed from the DLT and dead-lettered again (-dlt-dlt-dlt…).
    @KafkaListener(topicPattern = "payment\\.[a-z_]+",
                   groupId = "metrics-projection")
    @Transactional
    public void onPaymentEvent(String message,
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                               @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                               @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        try {
            JsonNode event = objectMapper.readTree(message);

            UUID eventKey = UUID.nameUUIDFromBytes(
                    (key + ":" + topic).getBytes(StandardCharsets.UTF_8));
            try {
                jdbc.update(
                        "INSERT INTO processed_events (event_id) VALUES (?)",
                        eventKey);
            } catch (DuplicateKeyException e) {
                log.debug("Duplicate event {} on {}, skipping", key, topic);
                return;
            }

            String status = event.get("status").asString();
            String currency = event.get("currency").asString();
            long amount = event.get("amount").asLong();
            String declineReason = event.hasNonNull("declineReason")
                    ? event.get("declineReason").asString() : "";


            Instant window = Instant.ofEpochMilli(timestamp)
                    .truncatedTo(ChronoUnit.HOURS);

            jdbc.update("""
                    INSERT INTO payment_metrics_hourly
                        (window_start, status, currency, decline_reason,
                         payment_count, volume_minor_units)
                    VALUES (?, ?, ?, ?, 1, ?)
                    ON CONFLICT (window_start, status, currency, decline_reason)
                    DO UPDATE SET
                        payment_count = payment_metrics_hourly.payment_count + 1,
                        volume_minor_units =
                            payment_metrics_hourly.volume_minor_units + ?
                    """,
                    Timestamp.from(window), status, currency, declineReason,
                    amount, amount);

            log.debug("Counted {} into payment_metrics_hourly window {}",
                    topic, window);
        } catch (Exception e) {
            log.error("Failed to process event on {}: {}", topic, message, e);
            throw new RuntimeException(e);   // retry → DLT
        }
    }
}
