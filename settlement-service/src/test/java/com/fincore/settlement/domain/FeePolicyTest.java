package com.fincore.settlement.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeePolicyTest {

    @Test
    void calculatesFeeForOneHundredDollars() {
        assertEquals(320, FeePolicy.calculate(10_000, "USD"));
    }

    @Test
    void roundsPercentageFeeToNearestMinorUnit() {
        assertEquals(31, FeePolicy.calculate(50, "USD"));
        assertEquals(32, FeePolicy.calculate(52, "USD"));
    }

    @Test
    void roundsExactHalfCentUp() {
        assertEquals(45, FeePolicy.calculate(500, "USD"));
    }

    @Test
    void appliesFixedFeeToSmallCaptures() {
        assertEquals(30, FeePolicy.calculate(1, "USD"));
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FeePolicy.calculate(0, "USD"));

        assertThrows(
                IllegalArgumentException.class,
                () -> FeePolicy.calculate(-1, "USD"));
    }

    @Test
    void rejectsUnsupportedCurrency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FeePolicy.calculate(10_000, "EUR"));
    }
}