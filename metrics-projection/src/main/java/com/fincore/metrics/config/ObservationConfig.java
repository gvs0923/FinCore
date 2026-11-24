package com.fincore.metrics.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ObservationConfig {

    // Scheduled rollup/retention jobs would otherwise flood the trace backend.
    @Bean
    ObservationPredicate skipScheduledTaskObservations() {
        return (name, context) -> !"tasks.scheduled.execution".equals(name);
    }
}
