package com.fincore.checkout.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class RiskAdminClient {

    private final RestClient restClient;

    public RiskAdminClient(
            @Value("${fincore.risk.base-url:http://localhost:9096}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<Map<String, Object>> reviewQueue() {
        return restClient.get()
                .uri("/v1/risk/review-queue")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public void markResolved(String paymentId, String action) {
        restClient.post()
                .uri("/v1/risk/review-queue/{id}/resolution?action={a}",
                        paymentId, action)
                .retrieve()
                .toBodilessEntity();
    }
}