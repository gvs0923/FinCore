package com.fincore.checkout.api.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public final class AiViews {

    private AiViews() {}

    /** risk-service row; internal only. */
    public record RiskDecisionRow(String payment_id, String decision, int score, String fired_rules,
            String signals, Double ml_probability, String ml_model_version, String resolution,
            String created_at) {}

    /** Exactly what ai-service may see: structured signals, no identifiers. */
    public record ExplainRequest(String decision, int score, List<String> firedRules,
            Map<String, Object> signals, Double mlProbability, String mlModelVersion) {}

    public record RiskExplanationText(
            @Schema(requiredMode = REQUIRED) String summary,
            @Schema(requiredMode = REQUIRED) List<String> citedRules,
            @Schema(requiredMode = REQUIRED) String generator,
            @Schema(requiredMode = REQUIRED, nullable = true) String fallbackReason) {}

    public record RiskExplanationView(
            @Schema(requiredMode = REQUIRED) String paymentId,
            @Schema(requiredMode = REQUIRED) String decision,
            @Schema(requiredMode = REQUIRED) int score,
            @Schema(requiredMode = REQUIRED) List<String> firedRules,
            @Schema(requiredMode = REQUIRED) Map<String, Object> signals,
            @Schema(requiredMode = REQUIRED, nullable = true) Double mlProbability,
            @Schema(requiredMode = REQUIRED, nullable = true) String mlModelVersion,
            @Schema(requiredMode = REQUIRED) String assessedAt,
            @Schema(requiredMode = REQUIRED, nullable = true) RiskExplanationText explanation,
            @Schema(requiredMode = REQUIRED, nullable = true) String explanationUnavailableReason) {}

    public record ReconDelta(
            @Schema(requiredMode = REQUIRED) String processorRef,
            @Schema(requiredMode = REQUIRED) long amountDeltaMinor,
            @Schema(requiredMode = REQUIRED) long feeDeltaMinor) {}

    public record ReconAggregates(
            @Schema(requiredMode = REQUIRED) String runId,
            @Schema(requiredMode = REQUIRED) String batchId,
            @Schema(requiredMode = REQUIRED) String reportRef,
            @Schema(requiredMode = REQUIRED) String currency,
            @Schema(requiredMode = REQUIRED) long itemCount,
            @Schema(requiredMode = REQUIRED) long matched,
            @Schema(requiredMode = REQUIRED) long mismatched,
            @Schema(requiredMode = REQUIRED) long missingInternal,
            @Schema(requiredMode = REQUIRED) long missingExternal,
            @Schema(requiredMode = REQUIRED) long amountOnlyMismatches,
            @Schema(requiredMode = REQUIRED) long feeOnlyMismatches,
            @Schema(requiredMode = REQUIRED) long amountAndFeeMismatches,
            @Schema(requiredMode = REQUIRED) long currencyMismatches,
            @Schema(requiredMode = REQUIRED) long netAmountDeltaMinor,
            @Schema(requiredMode = REQUIRED) long netFeeDeltaMinor,
            @Schema(requiredMode = REQUIRED) long missingInternalAmountMinor,
            @Schema(requiredMode = REQUIRED) long missingExternalAmountMinor,
            @Schema(requiredMode = REQUIRED) List<ReconDelta> largestDeltas) {}

    public record AnomalySummary(
            @Schema(requiredMode = REQUIRED) String summary,
            @Schema(requiredMode = REQUIRED) String generator,
            @Schema(requiredMode = REQUIRED, nullable = true) String fallbackReason) {}

    public record ReconAnomalyView(
            @Schema(requiredMode = REQUIRED) ReconAggregates aggregates,
            @Schema(requiredMode = REQUIRED, nullable = true) AnomalySummary summary,
            @Schema(requiredMode = REQUIRED, nullable = true) String summaryUnavailableReason) {}

    public record CopilotQuestion(@NotBlank @Size(min = 3, max = 500) String question) {}
}
