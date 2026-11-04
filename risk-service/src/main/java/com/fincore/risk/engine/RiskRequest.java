package com.fincore.risk.engine;

public record RiskRequest(String paymentId, long amount, String currency,
                          String cardFingerprint, String deviceId,
                          String ipAddress) {}