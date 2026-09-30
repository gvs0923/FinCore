package com.fincore.checkout.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateSessionRequest(
        @Min(1) long amount,
        @NotBlank String currency,
        @NotBlank String description
) {}
