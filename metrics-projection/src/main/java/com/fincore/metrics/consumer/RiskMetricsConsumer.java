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
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
            JsonNode event = objectMapper.readTree(message);

String identity = event.hasNonNull("eventId")
        ? event.get("eventId").asString()
        : key + ":" + topic + ":" + message;

UUID eventKey = UUID.nameUUIDFromBytes(
        ("risk-v2:" + identity).getBytes(StandardCharsets.UTF_8));

int inserted = jdbc.update("""
        INSERT INTO processed_events (event_id)
        VALUES (?)
        ON CONFLICT (event_id) DO NOTHING
        """, eventKey);

if (inserted == 0) {
    return;
}

String decision = event.get("decision").asString();

if (!Set.of("ALLOW", "REVIEW", "BLOCK").contains(decision)) {
    throw new IllegalArgumentException("Invalid risk decision");
}

Instant occurredAt = event.hasNonNull("occurredAt")
        ? Instant.parse(event.get("occurredAt").asString())
        : Instant.ofEpochMilli(ts);

Instant window = occurredAt.truncatedTo(ChronoUnit.HOURS);

int score = event.hasNonNull("score")
        ? event.get("score").asInt() : -1;

String bucket = score < 0 ? "UNKNOWN"
        : score < 30 ? "00-29"
        : score < 60 ? "30-59"
        : score < 90 ? "60-89"
        : "90+";

jdbc.update("""
        INSERT INTO risk_decision_metrics_hourly
            (window_start, decision, score_bucket, decision_count)
        VALUES (?, ?, ?, 1)
        ON CONFLICT (window_start, decision, score_bucket)
        DO UPDATE SET decision_count =
            risk_decision_metrics_hourly.decision_count + 1
        """, Timestamp.from(window), decision, bucket);

String firedRules = event.hasNonNull("firedRules")
        ? event.get("firedRules").asString() : "";

Set<String> rules = Arrays.stream(firedRules.split(","))
        .map(String::trim)
        .filter(rule -> !rule.isEmpty())
        .collect(Collectors.toSet());

for (String rule : rules) {
    if (rule.length() > 64) {
        throw new IllegalArgumentException("Invalid rule name");
    }

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