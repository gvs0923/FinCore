package com.fincore.metrics.rollup;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Rebuilds all rollup projections from scratch:
 *   1. truncate rollup + processed_events tables
 *   2. reset the "metrics-projection" consumer group to earliest
 *   3. Kafka replays every event; consumers re-upsert each row
 *
 * This is the proof that rollups are a derived projection, not a
 * second source of truth. Operational tool — not a normal code path.
 */
@Service
public class RebuildService {

    private static final Logger log = LoggerFactory.getLogger(RebuildService.class);
    private static final String GROUP_ID = "metrics-projection";

    private final JdbcTemplate jdbc;
    private final AdminClient adminClient;
    private final KafkaListenerEndpointRegistry registry;

    public RebuildService(JdbcTemplate jdbc,
                          AdminClient adminClient,
                          KafkaListenerEndpointRegistry registry) {
        this.jdbc = jdbc;
        this.adminClient = adminClient;
        this.registry = registry;
    }

    @Transactional
    public void truncateProjections() {
        jdbc.execute("TRUNCATE payment_metrics_hourly, payment_metrics_daily,"
                + " ledger_metrics_hourly, ledger_metrics_daily,"
                + " request_metrics_hourly, request_metrics_daily,"
                + " processed_events");
        log.warn("Truncated all rollup tables for rebuild");
    }

    /** Stop listeners → reset offsets → restart → replay. */
    public void rebuildAll() throws Exception {
        registry.getListenerContainers().forEach(c -> c.stop());
        try {
            truncateProjections();
            for (var entry : adminClient
                    .listConsumerGroupOffsets(GROUP_ID)
                    .partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS)
                    .entrySet()) {
                var earliest = adminClient.listOffsets(
                        Map.of(entry.getKey(), OffsetSpec.earliest()))
                        .all().get(10, TimeUnit.SECONDS)
                        .get(entry.getKey());
                adminClient.alterConsumerGroupOffsets(GROUP_ID,
                        Map.of(entry.getKey(),
                               new org.apache.kafka.clients.consumer
                                       .OffsetAndMetadata(earliest.offset())))
                        .all().get(10, TimeUnit.SECONDS);
            }
            log.warn("Consumer group {} reset to earliest — replay starting", GROUP_ID);
        } finally {
            registry.getListenerContainers().forEach(c -> c.start());
        }
    }
}