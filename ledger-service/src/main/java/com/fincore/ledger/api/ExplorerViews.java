package com.fincore.ledger.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ExplorerViews {

    private ExplorerViews() {
    }

    // One row of the chart of accounts, with its current projected
    // balance attached so the UI doesn't need N+1 calls.
    public record AccountRow(
            String accountRef,
            String accountType,
            String currency,
            BigDecimal balance) {
    }

    // One journal line — identity, direction, amount, and which
    // transaction it belongs to (for drill-through).
    public record EntryRow(
            UUID entryId,
            UUID transactionId,
            String reference,
            String entryType,
            BigDecimal amount,
            String currency,
            OffsetDateTime createdAt) {
    }

    // The whole journal: header + its lines. This is THE object that
    // makes double-entry visible — capture, refund, and payout all
    // render through it.
    public record JournalView(
            UUID transactionId,
            String reference,
            String description,
            OffsetDateTime createdAt,
            List<EntryRow> entries) {
    }
}