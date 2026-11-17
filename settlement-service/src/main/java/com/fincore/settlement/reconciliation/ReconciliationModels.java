package com.fincore.settlement.reconciliation;

import java.util.List;
import java.util.UUID;

public final class ReconciliationModels {

    private ReconciliationModels() {
    }

    public enum Classification {
        MATCHED,
        MISMATCHED,
        MISSING_INTERNAL,
        MISSING_EXTERNAL
    }

        // A single persisted comparison row — both sides' evidence.
    public record ItemResponse(
            String processorRef,
            String classification,
            Long internalAmountMinor,
            Long internalFeeMinor,
            String internalCurrency,
            Long externalAmountMinor,
            Long externalFeeMinor,
            String externalCurrency) {
    }

    public record SettlementRow(
            String processorRef,
            String currency,
            long amountMinor,
            long feeMinor) {

        public SettlementRow {
            if (processorRef == null || processorRef.isBlank()) {
                throw new IllegalArgumentException(
                        "Processor reference is required");
            }

            if (currency == null || !currency.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException(
                        "Currency must be a three-letter uppercase code");
            }

            if (amountMinor == 0 || feeMinor < 0) {
                throw new IllegalArgumentException(
                        "Amount must be non-zero and fee non-negative");
            }
        }
    }

    public record Comparison(
            String processorRef,
            Classification classification,
            SettlementRow internalRow,
            SettlementRow externalRow) {
    }

    public record ImportReportRequest(
            String reportRef,
            List<SettlementRow> rows) {

        public ImportReportRequest {
            if (reportRef == null || reportRef.isBlank()) {
                throw new IllegalArgumentException(
                        "Report reference is required");
            }
            if (rows == null) {
                throw new IllegalArgumentException("Rows are required");
            }
            rows = List.copyOf(rows);
        }
    }

    public record RunResponse(
            UUID id,
            UUID batchId,
            String reportRef,
            int itemCount,
            int matchedCount,
            int mismatchedCount,
            int missingInternalCount,
            int missingExternalCount,
            boolean breakDetected) {
    }
}