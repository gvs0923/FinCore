package com.fincore.checkout.client;

import com.fincore.checkout.api.admin.BackOfficeViews.*;
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

    public SettlementClient(@Value("${fincore.settlement.base-url}") String baseUrl, RestClient.Builder builder) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public SettlementBatch createBatch(String merchantId, String currency, String settlementDate) {
        return restClient.post().uri("/internal/settlement/batches")
                .body(Map.of("merchantId", merchantId, "currency", currency, "settlementDate", settlementDate))
                .retrieve().body(SettlementBatch.class);
    }

    public List<SettlementBatch> listBatches(int limit, long offset) {
        return restClient.get().uri("/internal/settlement/batches?limit={l}&offset={o}", limit, offset)
                .retrieve().body(new ParameterizedTypeReference<>() {});
    }

    public SettlementBatch getBatch(UUID batchId) {
        return restClient.get().uri("/internal/settlement/batches/{id}", batchId)
                .retrieve().body(SettlementBatch.class);
    }

    public List<BatchItem> batchItems(UUID batchId, int limit, long offset) {
        return restClient.get().uri("/internal/settlement/batches/{id}/items?limit={l}&offset={o}", batchId, limit, offset)
                .retrieve().body(new ParameterizedTypeReference<>() {});
    }

    public List<SettlementBatch> adjustments(UUID batchId, int limit, long offset) {
        return restClient.get().uri("/internal/settlement/batches/{id}/adjustments?limit={l}&offset={o}", batchId, limit, offset)
                .retrieve().body(new ParameterizedTypeReference<>() {});
    }

    public SettlementBatch payout(UUID batchId) {
        return restClient.post().uri("/internal/settlement/batches/{id}/payout", batchId)
                .retrieve().body(SettlementBatch.class);
    }

    public ReconciliationRun importReport(UUID batchId, ProcessorReport report) {
        return restClient.post().uri("/internal/settlement/batches/{id}/reconciliation", batchId)
                .body(report).retrieve().body(ReconciliationRun.class);
    }

    public List<ReconciliationRun> runs(UUID batchId) {
        return restClient.get().uri("/internal/settlement/batches/{id}/reconciliation-runs", batchId)
                .retrieve().body(new ParameterizedTypeReference<>() {});
    }

    public List<ReconciliationItem> runItems(UUID runId) {
        return restClient.get().uri("/internal/settlement/reconciliation-runs/{id}/items", runId)
                .retrieve().body(new ParameterizedTypeReference<>() {});
    }

    // Window is fixed (last 24h) by the overview, so one short-TTL entry is
    // enough to stop every dashboard poll from querying settlement-service.
    private static final long SUMMARY_TTL_MS = 15_000;
    private volatile SettlementSummary cachedSummary;
    private volatile long cachedSummaryAtMs;

    public SettlementSummary summary(java.time.Instant from, java.time.Instant to) {
        if (cachedSummary != null && System.currentTimeMillis() - cachedSummaryAtMs < SUMMARY_TTL_MS) {
            return cachedSummary;
        }
        SettlementSummary summary = restClient.get()
                .uri("/internal/settlement/summary?from={f}&to={t}", from, to)
                .retrieve().body(SettlementSummary.class);
        cachedSummaryAtMs = System.currentTimeMillis();
        return cachedSummary = summary;
    }

    public RefundResult refund(RefundRequest request) {
        return restClient.post().uri("/internal/settlement/refunds")
                .body(Map.of("paymentId", request.paymentId(),
                        "idempotencyKey", request.idempotencyKey(),
                        "reason", request.reason()))
                .retrieve().body(RefundResult.class);
    }
}
