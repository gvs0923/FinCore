package com.fincore.metrics.consumer;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate) {
        
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler handler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(1000L, 3));
        handler.setLogLevel(org.springframework.kafka.KafkaException.Level.WARN);
        return handler;
    }

    /** Used by RebuildService to rewind consumer-group offsets on replay. */
    @Bean
    public AdminClient kafkaAdminClient(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return AdminClient.create(
                java.util.Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        bootstrapServers));
    }
}
