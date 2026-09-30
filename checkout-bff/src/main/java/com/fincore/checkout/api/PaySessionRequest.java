package com.fincore.checkout.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Browser sends this when the user clicks "Pay"
public record PaySessionRequest(
        @NotBlank String cardNumber,
        @NotBlank String cardBrand,
        @NotBlank @Size(min = 4, max = 4) String cardLast4
) {}