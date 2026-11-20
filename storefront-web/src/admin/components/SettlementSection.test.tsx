import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import SettlementSection from "./SettlementSection";
import type { SettlementSummary } from "../api/adminClient";

function renderSection(settlement?: SettlementSummary | null, loading = false, error = false) {
  return render(
    <MemoryRouter>
      <SettlementSection settlement={settlement} loading={loading} error={error} />
    </MemoryRouter>,
  );
}

const empty: SettlementSummary = {
  currency: "USD",
  batches: [],
  refundCount: 0,
  refundMinor: 0,
  payoutCount: 0,
  payoutMinor: 0,
  reconciliationRuns: 0,
  reconciliationBreaks: 0,
};

describe("SettlementSection", () => {
  it("renders an honest empty state", () => {
    renderSection(empty);
    expect(screen.getByText("No settlement batches yet.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Batches" })).toHaveAttribute("href", "/settlement");
  });

  it("shows loading and unavailable states, including settlement-service down", () => {
    const { unmount } = renderSection(undefined, true);
    expect(screen.getByRole("status")).toHaveTextContent("Loading settlement metrics");
    unmount();
    renderSection(null);
    expect(screen.getByRole("alert")).toHaveTextContent("Settlement metrics are unavailable");
    expect(screen.queryByText("No settlement batches yet.")).not.toBeInTheDocument();
  });

  it("renders refund, payout and batch-state figures from minor units", () => {
    renderSection({
      ...empty,
      refundCount: 2,
      refundMinor: 12_345,
      reconciliationBreaks: 1,
      batches: [{ batchKind: "DAILY", status: "RECONCILED", count: 3, netMinor: 96_800 }],
    });
    expect(screen.getByText("$123.45")).toBeInTheDocument();
    expect(screen.getByText("DAILY · RECONCILED")).toBeInTheDocument();
    expect(screen.getByText("3 · net $968.00")).toBeInTheDocument();
  });
});
