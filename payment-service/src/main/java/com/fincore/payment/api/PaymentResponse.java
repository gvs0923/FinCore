package com.fincore.payment.api;

import com.fincore.payment.domain.Payment;

import java.time.OffsetDateTime;
import java.util.UUID;

// What we send back — never exposes card numbers or internal details
public record PaymentResponse(
        UUID id,
        String merchantRef,
        long amount,
        String currency,
        String status,
        String declineReason,
        String cardBrand,
        String cardLast4,
        OffsetDateTime createdAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getMerchantRef(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus().name(),
                p.getDeclineReason(),
                p.getCardBrand(),
                p.getCardLast4(),
                p.getCreatedAt()
        );
    }
}