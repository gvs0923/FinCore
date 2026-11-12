package com.fincore.checkout.metrics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class RequestMetricsInterceptor implements HandlerInterceptor {

    private static final String START_ATTR = "reqStartNanos";
    private final KafkaTemplate<String, String> kafka;

    public RequestMetricsInterceptor(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        request.setAttribute(START_ATTR, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        Long start = (Long) request.getAttribute(START_ATTR);
        if (start == null) return;

        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = request.getMethod() + " " + (pattern != null ? pattern : "UNMATCHED");
        int statusClass = (response.getStatus() / 100) * 100;   // 200, 400, 500

        String payload = String.format(
                "{\"route\":\"%s\",\"statusClass\":\"%dxx\",\"latencyMs\":%d,\"timestamp\":%d}",
                route, statusClass / 100, latencyMs, System.currentTimeMillis());

        kafka.send("request.metrics", route, payload);
    }
}