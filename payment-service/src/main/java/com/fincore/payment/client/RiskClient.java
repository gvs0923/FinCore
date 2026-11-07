package com.fincore.payment.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class RiskClient {

    private static final Logger log = LoggerFactory.getLogger(RiskClient.class);

    private final RestClient restClient;

    public RiskClient(@Value("${fincore.risk.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * Assess a payment. Returns null when risk-service is unreachable —
     * the caller decides the fallback policy; the client doesn't hide
     * outages as "ALLOW".
     */
    public Map<String, Object> assess(String paymentId, long amount,
                                      String currency, String cardFingerprint,
                                      String deviceId, String ipAddress) {
        try {
            return restClient.post()
                    .uri("/v1/risk/assess")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "paymentId", paymentId,
                            "amount", amount,
                            "currency", currency,
                            "cardFingerprint", cardFingerprint,
                            "deviceId", deviceId,
                            "ipAddress", ipAddress))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Risk assessment unavailable for payment {}: {}",
                    paymentId, e.getMessage());
            return null;
        }
    }
}