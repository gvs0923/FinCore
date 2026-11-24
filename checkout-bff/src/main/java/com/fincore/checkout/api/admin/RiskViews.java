package com.fincore.checkout.api.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public final class RiskViews {

    private RiskViews() {}

    public record DecisionCount(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String decision,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long count
    ) {}

    public record RuleCount(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String rule_fired,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long count
    ) {}

    public record ScoreCount(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String score_bucket,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long count
    ) {}

    public record RiskSummary(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<DecisionCount> byDecision,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<RuleCount> topRules,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<ScoreCount> scoreDistribution
    ) {}

    public record ReviewItem(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String payment_id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int score,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fired_rules,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String signals,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String created_at
    ) {}

    public record Overview(
            Map<String, String> window,
            Map<String, Object> payments,
            Map<String, Object> traffic,
            Map<String, Object> ledger,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RiskSummary risk,
            // null when settlement-service is unreachable
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            BackOfficeViews.SettlementSummary settlement,
            Map<String, Object> health
    ) {}
}