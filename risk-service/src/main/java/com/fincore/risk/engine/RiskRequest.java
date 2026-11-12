package com.fincore.risk.engine;

import java.util.Map;

public record RiskRequest(
        String paymentId,
        long amount,
        String currency,
        String cardFingerprint,
        String deviceId,
        String ipAddress,
        Map<String, String> context
) {

    public RiskRequest {
        context = context == null ? Map.of() : Map.copyOf(context);
    }

    public RiskRequest(String paymentId, long amount, String currency,
                       String cardFingerprint, String deviceId,
                       String ipAddress) {
        this(paymentId, amount, currency, cardFingerprint,
                deviceId, ipAddress, Map.of());
    }
}