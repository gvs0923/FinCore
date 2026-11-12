package com.fincore.checkout.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    private final RestClient restClient;

    public PaymentClient(@Value("${fincore.payment.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public Map<String, Object> createPayment(String idempotencyKey, String merchantRef,
                                              long amount, String currency,
                                              String cardNumber, String cardBrand,
                                              String cardLast4, String deviceId,
                                              String ipAddress,
                                              Map<String, String> context) {
        Map<String, Object> body = new HashMap<>();
        body.put("merchantRef", merchantRef);
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("cardNumber", cardNumber);
        body.put("cardBrand", cardBrand);
        body.put("cardLast4", cardLast4);
        body.put("deviceFingerprint", deviceId);
        body.put("ipAddress", ipAddress);
        body.put("context", context);

        Map<String, Object> result = restClient.post()
                .uri("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        log.info("Payment response for key {}: status={}", idempotencyKey, result.get("status"));
        return result;
    }

    public record PendingReview(String id, String createdAt) {}

    public List<PendingReview> pendingReviews() {
        return restClient.get()
                .uri("/v1/payments/pending-reviews")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> getPayment(String paymentId) {
        return restClient.get()
                .uri("/v1/payments/{id}", paymentId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> resolvePayment(String paymentId, String action) {
        return restClient.post()
                .uri("/v1/payments/{id}/resolve?action={a}", paymentId, action)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
