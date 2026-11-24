import { beforeEach, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import SettlementDetailPage from "./SettlementDetailPage";
import { fetchBatch, fetchBatchItems, fetchReconRuns, fetchAdjustments, payoutBatch, type AdminUser, type SettlementBatch } from "../api/adminClient";
import { AdminUserContext } from "../auth/adminUser";

vi.mock("../api/adminClient", () => ({
  fetchBatch: vi.fn(), fetchBatchItems: vi.fn(), fetchReconRuns: vi.fn(),
  fetchAdjustments: vi.fn(), payoutBatch: vi.fn(), importReport: vi.fn(),
}));

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(fetchBatch).mockResolvedValue({ id: "batch-1", batchKind: "DAILY", status: "RECONCILED",
    settlementDate: "2026-01-10", merchantId: "demo", currency: "USD",
    grossMinor: 10000, feesMinor: 320, refundsMinor: 0, netMinor: 9680 } as SettlementBatch);
  vi.mocked(fetchBatchItems).mockResolvedValue([]);
  vi.mocked(fetchReconRuns).mockResolvedValue([]);
  vi.mocked(fetchAdjustments).mockResolvedValue([]);
});

it.each(["VIEWER", "RISK_ANALYST", "FINANCE_OPS"])("limits payout controls for %s", async (role) => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}>
    <AdminUserContext.Provider value={{ name: "test", roles: [role] } as AdminUser}>
      <MemoryRouter initialEntries={["/settlement/batch-1"]}><Routes>
        <Route path="/settlement/:batchId" element={<SettlementDetailPage />} />
      </Routes></MemoryRouter>
    </AdminUserContext.Provider>
  </QueryClientProvider>);
  await screen.findByRole("heading", { level: 1 });
  if (role === "FINANCE_OPS") expect(screen.getByRole("button", { name: "Pay out" })).toBeEnabled();
  else expect(screen.queryByRole("button", { name: "Pay out" })).not.toBeInTheDocument();
  expect(payoutBatch).not.toHaveBeenCalled();
});
