package com.fincore.metrics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MetricsProjectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetricsProjectionApplication.class, args);
    }
}
