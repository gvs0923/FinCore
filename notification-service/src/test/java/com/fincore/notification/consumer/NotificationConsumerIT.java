package com.fincore.notification.consumer;

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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "spring.kafka.consumer.properties.metadata.max.age.ms=3000"
})
class NotificationConsumerIT {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:15-alpine")
                    .withStartupTimeout(Duration.ofSeconds(120));

    @Container
    static KafkaContainer kafka = newKafkaContainer();

    @SuppressWarnings("resource")
    private static KafkaContainer newKafkaContainer() {
        KafkaContainer c = new KafkaContainer("apache/kafka:3.8.1")
                .withStartupTimeout(Duration.ofSeconds(180));
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
    void duplicateEvent_producesOneNotification() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        String event = """
                {"paymentId":"%s","merchantRef":"ORD-1","amount":5000,
                 "currency":"USD","status":"CAPTURED","cardBrand":"VISA",
                 "cardLast4":"4242"}""".formatted(paymentId);

        kafkaTemplate.send("payment.captured", paymentId, event).get();
        kafkaTemplate.send("payment.captured", paymentId, event).get(); // redelivery

        awaitRows(1, Duration.ofSeconds(60));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT payment_id, topic, amount_minor_units FROM notifications");
        assertThat(row.get("payment_id")).isEqualTo(paymentId);
        assertThat(row.get("topic")).isEqualTo("payment.captured");
    }

    @Test
    void malformedMessage_landsOnDlt() throws Exception {
        String paymentId = UUID.randomUUID().toString();

        kafkaTemplate.send("payment.captured", paymentId, "not-json").get();

        // Nothing should be persisted for a poison message.
        Thread.sleep(10_000);
        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE payment_id = ?",
                Long.class, paymentId);
        assertThat(n).isZero();
    }

    private void awaitRows(long expected, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Long n = jdbc.queryForObject(
                    "SELECT count(*) FROM notifications", Long.class);
            if (n != null && n >= expected) return;
            Thread.sleep(500);
        }
        throw new AssertionError("Notification rows never appeared");
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
}