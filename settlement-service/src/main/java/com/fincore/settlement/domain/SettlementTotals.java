package com.fincore.settlement.domain;

public record SettlementTotals(
        long grossMinor,
        long feesMinor,
        long refundsMinor,
        long netMinor) {

    public SettlementTotals {
        if (grossMinor < 0 || feesMinor < 0 || refundsMinor < 0) {
            throw new IllegalArgumentException(
                    "Gross, fees, and refunds must be non-negative");
        }

        long expectedNet = Math.subtractExact(
                Math.subtractExact(grossMinor, feesMinor),
                refundsMinor);

        if (netMinor != expectedNet) {
            throw new IllegalArgumentException(
                    "Net must equal gross minus fees minus refunds");
        }
    }

    public static SettlementTotals of(
            long grossMinor,
            long feesMinor,
            long refundsMinor) {

        long netMinor = Math.subtractExact(
                Math.subtractExact(grossMinor, feesMinor),
                refundsMinor);

        return new SettlementTotals(
                grossMinor,
                feesMinor,
                refundsMinor,
                netMinor);
    }
}