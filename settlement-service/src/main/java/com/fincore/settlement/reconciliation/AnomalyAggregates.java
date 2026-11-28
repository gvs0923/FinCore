package com.fincore.settlement.reconciliation;

import java.util.List;
import java.util.UUID;

/** Aggregates over one run's persisted items: the only recon data the AI layer ever sees. */
public record AnomalyAggregates(
        UUID runId, UUID batchId, String reportRef, String currency,
        long itemCount, long matched, long mismatched, long missingInternal, long missingExternal,
        long amountOnlyMismatches, long feeOnlyMismatches, long amountAndFeeMismatches, long currencyMismatches,
        long netAmountDeltaMinor, long netFeeDeltaMinor,
        long missingInternalAmountMinor, long missingExternalAmountMinor,
        List<Delta> largestDeltas) {

    public record Delta(String processorRef, long amountDeltaMinor, long feeDeltaMinor) {}
}
