package com.fincore.notification.consumer;

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
import java.util.UUID;

@Component
public class NotificationConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationConsumer.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public NotificationConsumer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.objectMapper = new ObjectMapper();
    }

    // [a-z_]+ excludes "-", so it can't match our own -dlt topics.
    @KafkaListener(topicPattern = "payment\\.[a-z_]+",
                   groupId = "notification-service")
    @Transactional
    public void onPaymentEvent(String message,
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                               @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        JsonNode event;
        try {
            event = objectMapper.readTree(message);
        } catch (Exception e) {
            log.error("Malformed event on {}: {}", topic, message, e);
            throw new RuntimeException(e);   // retry → DLT
        }

        String eventName = switch (topic) {
            case "payment.captured"       -> "CAPTURED";
            case "payment.declined"       -> "DECLINED";
            case "payment.capture_failed" -> "CAPTURE_FAILED";
            default -> null;
        };
        if (eventName == null) return;   // other payment.* events aren't notifiable

        UUID eventId = UUID.nameUUIDFromBytes(
                (key + ":" + topic).getBytes(StandardCharsets.UTF_8));
        try {
            jdbc.update("""
                    INSERT INTO notifications
                        (event_id, payment_id, topic, merchant_ref,
                         amount_minor_units, currency)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    eventId,
                    key,
                    topic,
                    event.hasNonNull("merchantRef")
                            ? event.get("merchantRef").asString() : null,
                    event.hasNonNull("amount")
                            ? event.get("amount").asLong() : null,
                    event.hasNonNull("currency")
                            ? event.get("currency").asString() : null);
        } catch (DuplicateKeyException e) {
            log.debug("Duplicate event {} on {}, skipping", key, topic);
            return;
        }

        // Stub sender: log only. Never log PAN/CVV — the payload only carries
        // display-safe fields anyway.
        log.info("Notification [{}] payment={} merchantRef={} amount={} {}",
                eventName,
                key,
                event.path("merchantRef").asString(null),
                event.path("amount").asLong(),
                event.path("currency").asString());
    }
}