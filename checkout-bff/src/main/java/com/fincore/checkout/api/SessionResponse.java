package com.fincore.checkout.api;

import com.fincore.checkout.domain.CheckoutSession;

import java.util.UUID;

// What the browser gets back — clean, display-safe
public record SessionResponse(
        String sessionId,
        long amount,
        String currency,
        String description,
        String status,
        UUID paymentId,
        String failureReason
) {
    public static SessionResponse from(CheckoutSession s) {
        return new SessionResponse(
                s.getId(),
                s.getAmount(),
                s.getCurrency(),
                s.getDescription(),
                s.getStatus().name(),
                s.getPaymentId(),
                s.getFailureReason()
        );
    }
}