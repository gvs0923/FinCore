package com.fincore.metrics.consumer;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Consumer lag = how many events sit in Kafka that the metrics-projection
 * group has not yet committed. For each assigned partition:
 *   lag = log-end offset − committed offset.
 * A growing lag means consumers are down or too slow — the dashboard is
 * quietly going stale even if it doesn't know it yet.
 */
@Service
public class LagService {

    private static final Logger log = LoggerFactory.getLogger(LagService.class);
    private static final String GROUP_ID = "metrics-projection";

    private final AdminClient adminClient;

    public LagService(AdminClient adminClient) {
        this.adminClient = adminClient;
    }

    public Map<String, Object> lag() {
        try {
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata>
                    committed = adminClient.listConsumerGroupOffsets(GROUP_ID)
                        .partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);

            Map<TopicPartition, OffsetSpec> latestSpec = new HashMap<>();
            committed.keySet().forEach(tp -> latestSpec.put(tp, OffsetSpec.latest()));
            Map<TopicPartition, ListOffsetsResultInfo> endOffsets = adminClient
                    .listOffsets(latestSpec).all().get(10, TimeUnit.SECONDS);

            long total = 0;
            for (var entry : committed.entrySet()) {
                long end = endOffsets.get(entry.getKey()).offset();
                total += Math.max(0, end - entry.getValue().offset());
            }
            return Map.of("totalLag", total);
        } catch (Exception e) {
            log.warn("Could not compute consumer lag: {}", e.getMessage());
            // Lag unknown is itself meaningful — surface it, don't fake zero.
            return Map.of("totalLag", -1, "error", e.getMessage());
        }
    }
}
