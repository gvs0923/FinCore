package com.fincore.payment.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ObservationConfig {

    // The 500 ms outbox poller would otherwise emit 2 empty traces per second.
    @Bean
    ObservationPredicate skipScheduledTaskObservations() {
        return (name, context) -> !"tasks.scheduled.execution".equals(name);
    }
}
