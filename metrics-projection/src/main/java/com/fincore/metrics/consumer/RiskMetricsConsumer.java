package com.fincore.metrics.consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
public class RiskMetricsConsumer {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RiskMetricsConsumer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Deliberately not "payment.*": the payment.* consumers expect a
    // payment-status payload and would dead-letter this one.
    @KafkaListener(topics = "risk.assessed",
                   groupId = "metrics-projection")
    @Transactional
    public void onRiskEvent(String message,
                            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long ts,
                            @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        try {
            JsonNode e = objectMapper.readTree(message);

            UUID eventKey = UUID.nameUUIDFromBytes(
                    (key + ":" + topic).getBytes(StandardCharsets.UTF_8));
            try {
                jdbc.update("INSERT INTO processed_events (event_id) VALUES (?)",
                        eventKey);
            } catch (DuplicateKeyException dup) {
                return;
            }

            Instant window = Instant.ofEpochMilli(ts)
                    .truncatedTo(ChronoUnit.HOURS);
            String decision = e.get("decision").asString();
            String[] rules = e.get("firedRules").asString().isEmpty()
                    ? new String[]{""}
                    : e.get("firedRules").asString().split(",");

            for (String rule : rules) {
                jdbc.update("""
                        INSERT INTO risk_metrics_hourly
                            (window_start, decision, rule_fired, decision_count)
                        VALUES (?, ?, ?, 1)
                        ON CONFLICT (window_start, decision, rule_fired)
                        DO UPDATE SET decision_count =
                            risk_metrics_hourly.decision_count + 1
                        """, Timestamp.from(window), decision, rule);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);   // retry → DLT
        }
    }
}