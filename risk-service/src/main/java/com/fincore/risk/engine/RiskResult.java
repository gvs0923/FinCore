package com.fincore.risk.engine;

import java.util.List;

public record RiskResult(String decision, int score, List<String> firedRules) {}