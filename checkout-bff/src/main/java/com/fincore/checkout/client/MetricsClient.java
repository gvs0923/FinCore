package com.fincore.checkout.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MetricsClient {

    private static final Logger log = LoggerFactory.getLogger(MetricsClient.class);
    private static final long CACHE_TTL_MS = 15_000;   // dashboards poll;
    // without this every tile = a fresh query against metrics-projection

    private final RestClient restClient;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(Object body, long fetchedAtMs) {
        boolean fresh() {
            return System.currentTimeMillis() - fetchedAtMs < CACHE_TTL_MS;
        }
    }

    public MetricsClient(@Value("${fincore.metrics.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> paymentSummary(Instant from, Instant to) {
        return (Map<String, Object>) cached("summary:" + from + ":" + to, () ->
                restClient.get()
                        .uri("/metrics/payments/summary?from={f}&to={t}", from, to)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {}));
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> paymentTimeseries(Instant from, Instant to) {
        return (List<Map<String, Object>>) cached("ts:" + from + ":" + to, () ->
                restClient.get()
                        .uri("/metrics/payments/timeseries?from={f}&to={t}", from, to)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {}));
    }

    private Object cached(String key, java.util.function.Supplier<Object> fetch) {
        CacheEntry hit = cache.get(key);
        if (hit != null && hit.fresh()) return hit.body;
        Object body = fetch.get();
        cache.put(key, new CacheEntry(body, System.currentTimeMillis()));
        return body;
    }
}