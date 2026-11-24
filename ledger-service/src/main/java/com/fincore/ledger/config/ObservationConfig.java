package com.fincore.ledger.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ObservationConfig {

    // The 1s outbox poller would otherwise emit an empty trace every second.
    @Bean
    ObservationPredicate skipScheduledTaskObservations() {
        return (name, context) -> !"tasks.scheduled.execution".equals(name);
    }
}
