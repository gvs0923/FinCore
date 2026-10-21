package com.fincore.checkout.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Provides a mock KafkaTemplate for tests that exclude KafkaAutoConfiguration.
 * Without this, RequestMetricsInterceptor fails to wire.
 */
@TestConfiguration
public class TestKafkaConfig {

    @SuppressWarnings("unchecked")
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return Mockito.mock(KafkaTemplate.class);
    }
}
