package com.fincore.payment.outbox;

import com.fincore.payment.domain.OutboxEvent;
import com.fincore.payment.domain.Payment;
import com.fincore.payment.repository.OutboxEventRepository;
import com.fincore.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        // Don't block for the default 2min delivery timeout while the broker is dead
        "spring.kafka.producer.properties.delivery.timeout.ms=5000",
        "spring.kafka.producer.properties.request.timeout.ms=3000"
})
class KafkaChaosIT {

    // Colima starts containers slowly — give both a generous timeout, matching
    // the workaround in TestcontainersConfiguration.
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine")
            .withStartupTimeout(Duration.ofSeconds(120));

    // JAVA_TOOL_OPTIONS: the JDK in the image crashes with SIGILL under
    // Colima's vz VM (SVE probing on ARM) — -XX:UseSVE=0 works around it.
    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.1")
            .withEnv("JAVA_TOOL_OPTIONS", "-XX:UseSVE=0")
            .withStartupTimeout(Duration.ofSeconds(180));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // Colima port-forwarding delay: container is "ready" before the host
        // can actually reach the mapped port (same workaround as
        // TestcontainersConfiguration).
        waitForPort("localhost", postgres.getFirstMappedPort(), Duration.ofSeconds(30));
        waitForPort("localhost", kafka.getFirstMappedPort(), Duration.ofSeconds(30));
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired private PaymentService paymentService;
    @Autowired private OutboxEventRepository outboxRepo;

    @Test
    void brokerDeath_midFlow_noLostOrDuplicatedEvents() throws Exception {
        // 1. Happy path: payment while broker is UP → events get published
        Payment p1 = paymentService.processPayment(
                "chaos-" + System.nanoTime(), "ORDER-CHAOS-1",
                5000L, "USD", "4242424242424242", "VISA", "4242");
        awaitPublished(p1, Duration.ofSeconds(15));

        // 2. Freeze the broker (pause, not stop — stop/start would remap the
        //    port and the producer's bootstrap-servers is fixed at startup),
        //    then pay again — the API must still work. This is the entire
        //    point of the outbox: the write path doesn't need Kafka alive.
        kafka.getDockerClient()
                .pauseContainerCmd(kafka.getContainerId()).exec();
        Payment p2 = paymentService.processPayment(
                "chaos-" + System.nanoTime(), "ORDER-CHAOS-2",
                7000L, "USD", "4242424242424242", "VISA", "4242");

        // Give the publisher a few poll cycles to fail — events must STAY unpublished
        Thread.sleep(4000);
        assertThat(unpublishedFor(p2)).isNotEmpty();

        // 3. Broker comes back → publisher drains the backlog on its own
        kafka.getDockerClient()
                .unpauseContainerCmd(kafka.getContainerId()).exec();
        awaitPublished(p2, Duration.ofSeconds(30));

        // 4. No duplicates: one outbox row per event type per payment
        List<String> types = outboxRepo.findAll().stream()
                .filter(e -> e.getAggregateId().equals(p2.getId()))
                .map(OutboxEvent::getEventType).sorted().toList();
        assertThat(types).isEqualTo(
                List.of("payment.authorized", "payment.captured", "payment.created"));
    }

    private List<OutboxEvent> unpublishedFor(Payment p) {
        return outboxRepo.findUnpublished().stream()
                .filter(e -> e.getAggregateId().equals(p.getId())).toList();
    }

    private static void waitForPort(String host, int port, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try (java.net.Socket s = new java.net.Socket(host, port)) {
                return;
            } catch (Exception e) {
                try { Thread.sleep(200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        throw new IllegalStateException("Port " + port + " not reachable after " + timeout);
    }

    private void awaitPublished(Payment p, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (unpublishedFor(p).isEmpty()) return;
            Thread.sleep(500);
        }
        throw new AssertionError("Events for " + p.getId() + " never published");
    }
}