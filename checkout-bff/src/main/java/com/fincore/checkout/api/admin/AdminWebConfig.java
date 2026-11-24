package com.fincore.checkout.api.admin;

import com.fincore.checkout.metrics.RequestMetricsInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    private final RequestMetricsInterceptor requestMetrics;

    public AdminWebConfig(RequestMetricsInterceptor requestMetrics) {
        this.requestMetrics = requestMetrics;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Admin authz moved to SecurityConfig: one filter-chain matcher on /v1/admin/**.
        registry.addInterceptor(requestMetrics).addPathPatterns("/v1/**");
    }
}