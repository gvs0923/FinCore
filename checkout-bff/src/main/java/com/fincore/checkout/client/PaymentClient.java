package com.fincore.checkout.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
                                              String cardLast4) {
        Map<String, Object> body = Map.of(
                "merchantRef", merchantRef,
                "amount", amount,
                "currency", currency,
                "cardNumber", cardNumber,
                "cardBrand", cardBrand,
                "cardLast4", cardLast4
        );

        Map result = restClient.post()
                .uri("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(body)
                .retrieve()
                .body(Map.class);

        log.info("Payment response for key {}: status={}", idempotencyKey, result.get("status"));
        return result;
    }
}
