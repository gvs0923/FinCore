package com.fincore.metrics.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {

        "spring.kafka.consumer.properties.metadata.max.age.ms=3000"
})
class MetricsRollupIT {

    @Autowired
    private RiskMetricsConsumer riskConsumer;

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine")
            .withStartupTimeout(Duration.ofSeconds(120));

    @Container
    static KafkaContainer kafka = newKafkaContainer();

    @SuppressWarnings("resource")
    private static KafkaContainer newKafkaContainer() {
        KafkaContainer c = new KafkaContainer("apache/kafka:3.8.1")
                .withStartupTimeout(Duration.ofSeconds(180));
        // -XX:UseSVE=0 is only needed on aarch64 (Apple Silicon);
        // setting it unconditionally crashes the JVM on x86 CI runners.
        if ("aarch64".equals(System.getProperty("os.arch"))) {
            c.withEnv("JAVA_TOOL_OPTIONS", "-XX:UseSVE=0");
        }
        return c;
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        waitForPort("localhost", postgres.getFirstMappedPort(), Duration.ofSeconds(30));
        waitForPort("localhost", kafka.getFirstMappedPort(), Duration.ofSeconds(30));
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void eventsRollUp_andReplayDoesNotDoubleCount() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        String captured = """
                {"paymentId":"%s","merchantRef":"ORD-1","amount":5000,
                 "currency":"USD","status":"CAPTURED","cardBrand":"VISA",
                 "cardLast4":"4242"}""".formatted(paymentId);
        String authorized = captured.replace("CAPTURED", "AUTHORIZED");

        kafkaTemplate.send("payment.captured", paymentId, captured).get();
        kafkaTemplate.send("payment.captured", paymentId, captured).get();
        kafkaTemplate.send("payment.authorized", paymentId, authorized).get();

        awaitRollup(2, Duration.ofSeconds(60));

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT status, payment_count, volume_minor_units
                FROM payment_metrics_hourly ORDER BY status""");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("status")).isEqualTo("AUTHORIZED");
        assertThat(((Number) rows.get(0).get("payment_count")).longValue()).isEqualTo(1L);
        assertThat(rows.get(1).get("status")).isEqualTo("CAPTURED");
        assertThat(((Number) rows.get(1).get("payment_count")).longValue()).isEqualTo(1L);
        assertThat(((Number) rows.get(1).get("volume_minor_units")).longValue()).isEqualTo(5000L);
    }

    private void awaitRollup(long expectedRows, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Long n = jdbc.queryForObject(
                    "SELECT count(*) FROM payment_metrics_hourly", Long.class);
            if (n != null && n >= expectedRows) return;
            Thread.sleep(500);
        }
        throw new AssertionError("Rollup rows never appeared");
    }

    private static void waitForPort(String host, int port, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try (Socket s = new Socket(host, port)) {
                return;
            } catch (Exception e) {
                try { Thread.sleep(200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        throw new IllegalStateException("Port " + port + " not reachable after " + timeout);
    }

    @Test
void riskEventCountsOneDecisionAndTwoRulesDespiteRedelivery() {
    String eventId = UUID.randomUUID().toString();
    String paymentId = UUID.randomUUID().toString();

    java.time.Instant occurredAt = java.time.Instant.now();
    java.sql.Timestamp window = java.sql.Timestamp.from(
            occurredAt.truncatedTo(java.time.temporal.ChronoUnit.HOURS));

    Long decisionsBefore = jdbc.queryForObject("""
            SELECT COALESCE(SUM(decision_count), 0)
            FROM risk_decision_metrics_hourly
            WHERE window_start = ?
              AND decision = 'REVIEW'
              AND score_bucket = '30-59'
            """, Long.class, window);

    Long rulesBefore = jdbc.queryForObject("""
            SELECT COALESCE(SUM(decision_count), 0)
            FROM risk_metrics_hourly
            WHERE window_start = ?
              AND decision = 'REVIEW'
              AND rule_fired IN ('high_amount', 'new_device')
            """, Long.class, window);

    String event = """
            {
              "eventId":"%s",
              "occurredAt":"%s",
              "paymentId":"%s",
              "decision":"REVIEW",
              "score":55,
              "firedRules":"high_amount,new_device"
            }
            """.formatted(eventId, occurredAt, paymentId);

    riskConsumer.onRiskEvent(
            event, "risk.assessed", occurredAt.toEpochMilli(), paymentId);

    riskConsumer.onRiskEvent(
            event, "risk.assessed", occurredAt.toEpochMilli(), paymentId);

    Long decisionsAfter = jdbc.queryForObject("""
            SELECT COALESCE(SUM(decision_count), 0)
            FROM risk_decision_metrics_hourly
            WHERE window_start = ?
              AND decision = 'REVIEW'
              AND score_bucket = '30-59'
            """, Long.class, window);

    Long rulesAfter = jdbc.queryForObject("""
            SELECT COALESCE(SUM(decision_count), 0)
            FROM risk_metrics_hourly
            WHERE window_start = ?
              AND decision = 'REVIEW'
              AND rule_fired IN ('high_amount', 'new_device')
            """, Long.class, window);

    assertThat(decisionsAfter - decisionsBefore).isEqualTo(1L);
    assertThat(rulesAfter - rulesBefore).isEqualTo(2L);
    }
}
