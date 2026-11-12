package com.fincore.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

// What the caller sends to create a payment
public record CreatePaymentRequest(
        @NotBlank String merchantRef,          // merchant's order ID
        @Positive long amount,                 // in minor units (cents)
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotBlank String cardNumber,           // test PAN only (4242...)
        @NotBlank String cardBrand,            // VISA, MASTERCARD etc.
        @NotBlank @Size(min = 4, max = 4) String cardLast4,
        String deviceFingerprint,
        String ipAddress,
        Map<String, String> context
) {}
