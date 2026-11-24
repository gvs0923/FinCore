package com.fincore.checkout.observability;

import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class EdgeSamplingConfig {

    // NOT parentBased: a client-sent "-01" flag can't force 100% sampling.
    @Bean
    Sampler edgeSampler(
            @Value("${management.tracing.sampling.probability}") double probability) {
        return Sampler.traceIdRatioBased(probability);
    }
}
