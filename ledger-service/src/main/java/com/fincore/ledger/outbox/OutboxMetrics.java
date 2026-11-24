package com.fincore.ledger.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class OutboxMetrics {

    OutboxMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
        Gauge.builder("fincore.outbox.pending", jdbc, j -> number(j.queryForObject(
                        "SELECT count(*) FROM ledger_outbox_events WHERE published_at IS NULL", Long.class)))
                .description("Outbox rows not yet published to Kafka")
                .register(registry);
        Gauge.builder("fincore.outbox.oldest.age", jdbc, j -> number(j.queryForObject("""
                        SELECT COALESCE(EXTRACT(EPOCH FROM now() - min(created_at)), 0)
                        FROM ledger_outbox_events WHERE published_at IS NULL""", Double.class)))
                .baseUnit("seconds")
                .description("Age of the oldest unpublished outbox row")
                .register(registry);
    }

    private static double number(Number n) {
        return n == null ? 0 : n.doubleValue();
    }
}
