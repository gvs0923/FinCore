package com.fincore.settlement.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementTotalsTest {

    @Test
    void calculatesNetAfterFeesAndRefunds() {
        SettlementTotals totals = SettlementTotals.of(
                10_000, 320, 2_000);

        assertEquals(7_680, totals.netMinor());
    }

    @Test
    void supportsAnEmptyBatchTotal() {
        assertEquals(
                new SettlementTotals(0, 0, 0, 0),
                SettlementTotals.of(0, 0, 0));
    }

    @Test
    void representsNegativeNetWithoutInventingAPayout() {
        SettlementTotals totals = SettlementTotals.of(
                0, 0, 10_000);

        assertEquals(-10_000, totals.netMinor());
    }

    @Test
    void rejectsNegativeComponents() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementTotals.of(-1, 0, 0));

        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementTotals.of(0, -1, 0));

        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementTotals.of(0, 0, -1));
    }

    @Test
    void rejectsInconsistentNet() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementTotals(10_000, 320, 0, 10_000));
    }

    @Test
    void rejectsArithmeticOverflow() {
        assertThrows(
                ArithmeticException.class,
                () -> SettlementTotals.of(
                        0, Long.MAX_VALUE, Long.MAX_VALUE));
    }
}