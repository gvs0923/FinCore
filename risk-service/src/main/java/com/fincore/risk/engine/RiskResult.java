package com.fincore.risk.engine;

import java.util.List;
import java.util.Map;

public record RiskResult(
        String decision,
        int score,
        List<String> firedRules,
        Map<String, Object> signals
) {

    public RiskResult(String decision, int score, List<String> firedRules) {
        this(decision, score, firedRules, Map.of());
    }
}