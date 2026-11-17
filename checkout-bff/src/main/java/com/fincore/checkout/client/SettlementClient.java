package com.fincore.checkout.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SettlementClient {

    private final RestClient restClient;

    public SettlementClient(
            @Value("${fincore.settlement.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl).build();
    }

    public Map<String, Object> createBatch(
            String merchantId, String currency, String settlementDate) {
        return restClient.post()
                .uri("/internal/settlement/batches")
                .body(Map.of(
                        "merchantId", merchantId,
                        "currency", currency,
                        "settlementDate", settlementDate))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public List<Map<String, Object>> listBatches(int limit, long offset) {
        return restClient.get()
                .uri("/internal/settlement/batches?limit={l}&offset={o}",
                        limit, offset)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> getBatch(UUID batchId) {
        return restClient.get()
                .uri("/internal/settlement/batches/{id}", batchId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public List<Map<String, Object>> batchItems(
            UUID batchId, int limit, long offset) {
        return restClient.get()
                .uri("/internal/settlement/batches/{id}/items"
                        + "?limit={l}&offset={o}",
                        batchId, limit, offset)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> payout(UUID batchId) {
        return restClient.post()
                .uri("/internal/settlement/batches/{id}/payout", batchId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> importReport(
            UUID batchId, Map<String, Object> report) {
        return restClient.post()
                .uri("/internal/settlement/batches/{id}/reconciliation",
                        batchId)
                .body(report)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> refund(Map<String, Object> refundRequest) {
        return restClient.post()
                .uri("/internal/settlement/refunds")
                .body(refundRequest)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}