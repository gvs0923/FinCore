package com.fincore.checkout.api;

import jakarta.validation.constraints.NotBlank;

public record PaySessionRequest(
        @NotBlank String cardNumber,
        @NotBlank String cardBrand,
        @NotBlank String cardLast4,
        String deviceId,
        String timezone
) {}
