package com.fincore.checkout.api;

import com.fincore.checkout.domain.CheckoutSession;

import java.util.UUID;

public record SessionResponse(
        String sessionId,
        long amount,
        String currency,
        String description,
        String status,
        UUID paymentId,
        String failureReason
) {
    public static SessionResponse from(CheckoutSession session) {
        return new SessionResponse(
                session.getId(),
                session.getAmount(),
                session.getCurrency(),
                session.getDescription(),
                session.getStatus().name(),
                session.getPaymentId(),
                session.getFailureReason()
        );
    }
}
