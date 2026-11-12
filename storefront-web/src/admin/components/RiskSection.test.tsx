import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import RiskSection from "./RiskSection";
import type { RiskSummary } from "../api/adminClient";

function renderSection(risk?: RiskSummary, loading = false, error = false) {
  return render(
    <MemoryRouter>
      <RiskSection risk={risk} loading={loading} error={error} />
    </MemoryRouter>,
  );
}

const empty: RiskSummary = { byDecision: [], topRules: [], scoreDistribution: [] };

describe("RiskSection", () => {
  it("renders an honest empty state", () => {
    renderSection(empty);
    expect(screen.getByText("No assessments in this window.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Review queue" })).toHaveAttribute("href", "/risk");
  });

  it("shows loading and unavailable states", () => {
    const { unmount } = renderSection(undefined, true);
    expect(screen.getByRole("status")).toHaveTextContent("Loading risk metrics");
    unmount();
    renderSection(undefined, false, true);
    expect(screen.getByRole("alert")).toHaveTextContent("Risk metrics are unavailable");
  });

  it("does not crash or claim an empty distribution when an older service returns null", () => {
    renderSection({ ...empty, scoreDistribution: null } as unknown as RiskSummary);
    expect(screen.getByRole("alert")).toHaveTextContent("Risk metrics are unavailable");
    expect(screen.queryByText("No assessments in this window.")).not.toBeInTheDocument();
  });

  it("renders decision counts and triggering rules", () => {
    renderSection({
      ...empty,
      byDecision: [{ decision: "REVIEW", count: 3 }],
      topRules: [{ rule_fired: "country_changed", count: 2 }],
    });
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("country_changed")).toBeInTheDocument();
  });
});
