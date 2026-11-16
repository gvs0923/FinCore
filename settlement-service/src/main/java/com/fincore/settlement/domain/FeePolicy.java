package com.fincore.settlement.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FeePolicy {

    public static final String VERSION = "USD_2_9_PERCENT_PLUS_30_V1";

    private static final BigDecimal RATE = new BigDecimal("0.029");
    private static final long FIXED_FEE_MINOR = 30;

    private FeePolicy() {
    }

    public static long calculate(long amountMinor, String currency) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Capture amount must be positive");
        }
        if (!"USD".equals(currency)) {
            throw new IllegalArgumentException("Fee policy supports USD only");
        }

        long percentageFee = BigDecimal.valueOf(amountMinor)
                .multiply(RATE)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        return Math.addExact(percentageFee, FIXED_FEE_MINOR);
    }
}