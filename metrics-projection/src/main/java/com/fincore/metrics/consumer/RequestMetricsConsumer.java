package com.fincore.metrics.consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class RequestMetricsConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(RequestMetricsConsumer.class);
    private static final long[] BUCKETS = {50, 100, 250, 500, 1000, 2500};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RequestMetricsConsumer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.objectMapper = new ObjectMapper();
    }

    @KafkaListener(topics = "request.metrics",
                   groupId = "metrics-projection")
    @Transactional
    public void onRequestMetric(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String route = event.get("route").asString();
            String statusClass = event.get("statusClass").asString();
            long latencyMs = event.get("latencyMs").asLong();
            long ts = event.get("timestamp").asLong();

            Instant window = Instant.ofEpochMilli(ts)
                    .truncatedTo(ChronoUnit.HOURS);

            // Determine which histogram buckets this latency falls into
            // Histogram buckets are cumulative: if latency=120ms, it
            // increments le_250, le_500, le_1000, le_2500, le_inf
            int le50  = latencyMs <= 50   ? 1 : 0;
            int le100 = latencyMs <= 100  ? 1 : 0;
            int le250 = latencyMs <= 250  ? 1 : 0;
            int le500 = latencyMs <= 500  ? 1 : 0;
            int le1000 = latencyMs <= 1000 ? 1 : 0;
            int le2500 = latencyMs <= 2500 ? 1 : 0;

            jdbc.update("""
                    INSERT INTO request_metrics_hourly
                        (window_start, route, status_class, hit_count,
                         le_50, le_100, le_250, le_500, le_1000, le_2500, le_inf)
                    VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?, 1)
                    ON CONFLICT (window_start, route, status_class)
                    DO UPDATE SET
                        hit_count = request_metrics_hourly.hit_count + 1,
                        le_50 = request_metrics_hourly.le_50 + ?,
                        le_100 = request_metrics_hourly.le_100 + ?,
                        le_250 = request_metrics_hourly.le_250 + ?,
                        le_500 = request_metrics_hourly.le_500 + ?,
                        le_1000 = request_metrics_hourly.le_1000 + ?,
                        le_2500 = request_metrics_hourly.le_2500 + ?,
                        le_inf = request_metrics_hourly.le_inf + 1
                    """,
                    Timestamp.from(window), route, statusClass,
                    le50, le100, le250, le500, le1000, le2500,
                    le50, le100, le250, le500, le1000, le2500);
        } catch (Exception e) {
            log.error("Failed to process request metric: {}", message, e);
        }
    }
}