package com.fincore.checkout.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;


public record CreateSessionRequest(
        @Positive long amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotBlank String description
) {}
