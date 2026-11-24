package com.fincore.checkout.api.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public final class BackOfficeViews {
    private BackOfficeViews() {}

    public record SettlementBatch(UUID id, String merchantId, String currency,
            LocalDate settlementDate, String status, long captureCount,
            long grossMinor, long feesMinor, long refundsMinor, long netMinor, String batchKind,
            @io.swagger.v3.oas.annotations.media.Schema(nullable = true) UUID originalBatchId,
            @io.swagger.v3.oas.annotations.media.Schema(nullable = true) UUID adjustmentRefundId) {}
    public record BatchItem(UUID paymentId, String processorRef, long amountMinor,
            long feeMinor, String feePolicyVersion) {}
    public record ReconciliationRun(UUID id, UUID batchId, String reportRef,
            int itemCount, int matchedCount, int mismatchedCount,
            int missingInternalCount, int missingExternalCount, boolean breakDetected) {}
    public record ReconciliationItem(String processorRef, String classification,
            Long internalAmountMinor, Long internalFeeMinor, String internalCurrency,
            Long externalAmountMinor, Long externalFeeMinor, String externalCurrency) {}
    public record ProcessorRow(String processorRef, String currency, long amountMinor, long feeMinor) {}
    public record ProcessorReport(String reportRef, List<ProcessorRow> rows) {}
    public record RefundRequest(@jakarta.validation.constraints.NotNull UUID paymentId,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 255) String idempotencyKey,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 1000) String reason) {}
    public record RefundResult(UUID id, UUID paymentId, long amountMinor,
            String currency, String status, boolean replayed,
            @io.swagger.v3.oas.annotations.media.Schema(nullable = true) UUID adjustmentBatchId) {}
    public record LedgerAccount(String accountRef, String accountType, String currency, BigDecimal balance) {}
    public record LedgerEntryRow(UUID entryId, UUID transactionId, String reference,
            String entryType, BigDecimal amount, String currency, OffsetDateTime createdAt) {}
    public record LedgerJournal(UUID transactionId, String reference, String description,
            OffsetDateTime createdAt, List<LedgerEntryRow> entries) {}
    public record SettlementSummary(@Schema(requiredMode = REQUIRED) String currency,
            @Schema(requiredMode = REQUIRED) List<BatchStatusCount> batches,
            @Schema(requiredMode = REQUIRED) long refundCount,
            @Schema(requiredMode = REQUIRED) long refundMinor,
            @Schema(requiredMode = REQUIRED) long payoutCount,
            @Schema(requiredMode = REQUIRED) long payoutMinor,
            @Schema(requiredMode = REQUIRED) long reconciliationRuns,
            @Schema(requiredMode = REQUIRED) long reconciliationBreaks) {}
    public record BatchStatusCount(@Schema(requiredMode = REQUIRED) String batchKind,
            @Schema(requiredMode = REQUIRED) String status,
            @Schema(requiredMode = REQUIRED) long count,
            @Schema(requiredMode = REQUIRED) long netMinor) {}
    public record Order(UUID id, String merchantRef, long amount, String currency,
            String status, String declineReason, String cardBrand, String cardLast4,
            OffsetDateTime createdAt) {}
}
