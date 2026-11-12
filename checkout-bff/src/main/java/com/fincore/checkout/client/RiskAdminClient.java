package com.fincore.checkout.client;

import com.fincore.checkout.api.admin.RiskViews;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Component
public class RiskAdminClient {

    private final RestClient restClient;

    public RiskAdminClient(
            @Value("${fincore.risk.base-url:http://localhost:9096}") String baseUrl) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public List<RiskViews.ReviewItem> reviewQueue() {
        return restClient.get()
                .uri("/v1/risk/review-queue")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public List<RiskViews.ReviewItem> reviewDetails(List<String> paymentIds) {
        return restClient.post()
                .uri("/v1/risk/review-details")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(paymentIds)
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