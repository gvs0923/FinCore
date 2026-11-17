package com.fincore.settlement.service;

import com.fincore.settlement.consumer.CapturedPayment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.task.scheduling.enabled=false"
})
class CaptureIngestionIT {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:15-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        waitForPort("localhost", postgres.getFirstMappedPort());
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static void waitForPort(String host, int port) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            try (java.net.Socket ignored = new java.net.Socket(host, port)) {
                return;
            } catch (Exception e) {
                try { Thread.sleep(200); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new IllegalStateException(
                "Postgres port not reachable in time");
    }

    @Autowired
    private CaptureIngestionService ingestionService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void identicalRedeliveryCreatesOneCapture() {
        CapturedPayment capture = capture(UUID.randomUUID(), 10_000);

        ingestionService.ingest(capture);
        ingestionService.ingest(capture);

        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM settlement_captures
                WHERE payment_id = ?
                """,
                Long.class,
                capture.paymentId());

        assertEquals(1L, count.longValue());
    }

    @Test
    void conflictingRedeliveryDoesNotChangeOriginal() {
        UUID paymentId = UUID.randomUUID();

        ingestionService.ingest(capture(paymentId, 10_000));

        assertThrows(
                IllegalStateException.class,
                () -> ingestionService.ingest(
                        capture(paymentId, 20_000)));

        Long amount = jdbc.queryForObject("""
                SELECT amount_minor
                FROM settlement_captures
                WHERE payment_id = ?
                """,
                Long.class,
                paymentId);

        assertEquals(10_000L, amount.longValue());
    }

    private static CapturedPayment capture(
            UUID paymentId,
            long amount) {
        return new CapturedPayment(
                paymentId,
                "demo-merchant",
                "checkout-" + paymentId,
                "capture-" + paymentId,
                amount,
                "USD",
                Instant.parse("2026-11-16T12:00:00Z"));
    }
}