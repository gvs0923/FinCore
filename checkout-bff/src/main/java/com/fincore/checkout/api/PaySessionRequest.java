package com.fincore.checkout.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record PaySessionRequest(
        @NotBlank String cardNumber,
        @NotBlank String cardBrand,
        @NotBlank String cardLast4,
        @Size(max = 128) String deviceId,
        @Size(max = 3) Map<String, String> context
) {}
