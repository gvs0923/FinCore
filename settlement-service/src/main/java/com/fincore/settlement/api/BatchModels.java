package com.fincore.settlement.api;

import java.time.LocalDate;
import java.util.UUID;

public final class BatchModels {

    private BatchModels() {
    }

    public record CreateBatchRequest(
            String merchantId,
            String currency,
            LocalDate settlementDate) {
    }

    public record BatchResponse(
            UUID id,
            String merchantId,
            String currency,
            LocalDate settlementDate,
            String status,
            long captureCount,
            long grossMinor,
            long feesMinor,
            long refundsMinor,
            long netMinor,
            String batchKind,
            UUID originalBatchId,
            UUID adjustmentRefundId) {
    }

    public record BatchItemResponse(
            UUID paymentId,
            String processorRef,
            long amountMinor,
            long feeMinor,
            String feePolicyVersion) {
    }

        public record RefundRequest(
            UUID paymentId,
            String idempotencyKey,
            String reason,
            String actor) {
    }

    public record RefundResponse(
            UUID id,
            UUID paymentId,
            long amountMinor,
            String currency,
            String status,
            boolean replayed,
            UUID adjustmentBatchId) {
    }
}