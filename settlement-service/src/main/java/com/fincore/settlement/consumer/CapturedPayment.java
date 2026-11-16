package com.fincore.settlement.consumer;

import java.time.Instant;
import java.util.UUID;

public record CapturedPayment(
        UUID paymentId,
        String merchantId,
        String merchantRef,
        String processorRef,
        long amountMinor,
        String currency,
        Instant capturedAt) {

    public CapturedPayment {
        if (paymentId == null || capturedAt == null) {
            throw new IllegalArgumentException(
                    "Payment ID and capture time are required");
        }

        if (merchantId == null || merchantId.isBlank()
                || merchantId.length() > 128) {
            throw new IllegalArgumentException("Invalid merchant ID");
        }

        if (merchantRef == null || merchantRef.isBlank()) {
            throw new IllegalArgumentException(
                    "Merchant reference is required");
        }

        if (processorRef == null || processorRef.isBlank()) {
            throw new IllegalArgumentException(
                    "Processor reference is required");
        }

        if (amountMinor <= 0) {
            throw new IllegalArgumentException(
                    "Capture amount must be positive");
        }

        if (!"USD".equals(currency)) {
            throw new IllegalArgumentException(
                    "Settlement currently supports USD only");
        }
    }
}