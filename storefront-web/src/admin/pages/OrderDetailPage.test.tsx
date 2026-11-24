import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import OrderDetailPage from "./OrderDetailPage";
import { fetchOrder, fetchJournal, refundPayment, type AdminUser } from "../api/adminClient";
import { AdminUserContext } from "../auth/adminUser";

vi.mock("../api/adminClient", () => ({ fetchOrder: vi.fn(), fetchJournal: vi.fn(), refundPayment: vi.fn() }));

function show(roles = ["VIEWER", "FINANCE_OPS"]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}>
    <AdminUserContext.Provider value={{ name: "test", roles } as AdminUser}>
    <MemoryRouter initialEntries={["/payments/payment-1"]}><Routes>
      <Route path="/payments/:paymentId" element={<OrderDetailPage />} />
    </Routes></MemoryRouter>
    </AdminUserContext.Provider>
  </QueryClientProvider>);
}

beforeEach(() => {
  vi.resetAllMocks();
  sessionStorage.clear();
  vi.mocked(fetchOrder).mockResolvedValue({ id: "payment-1", merchantRef: "order-1", amount: 10000,
    currency: "USD", status: "CAPTURED", declineReason: "", cardBrand: "Visa", cardLast4: "4242", createdAt: "2026-01-10T10:00:00Z" });
  vi.mocked(fetchJournal).mockResolvedValue({ transactionId: "tx-1", reference: "payment:payment-1",
    description: "Capture", createdAt: "2026-01-10T10:00:00Z", entries: [] });
});

describe("order refund", () => {
  it("does not expose refunds to a viewer", async () => {
    show(["VIEWER"]);
    await screen.findByRole("heading", { name: "Order order-1" });
    expect(screen.queryByRole("button", { name: "Refund payment" })).not.toBeInTheDocument();
    expect(refundPayment).not.toHaveBeenCalled();
  });

  it("requires confirmation and reuses the attempt after a network failure and remount", async () => {
    const user = userEvent.setup();
    vi.mocked(refundPayment).mockRejectedValueOnce(new Error("Network interrupted"))
      .mockResolvedValue({ id: "refund-1", paymentId: "payment-1", amountMinor: 10000,
        currency: "USD", status: "REFUNDED", replayed: true, adjustmentBatchId: null });
    const first = show();
    await user.type(await screen.findByRole("textbox", { name: "Refund reason" }), "Customer request");
    await user.click(screen.getByRole("button", { name: "Refund payment" }));
    expect(refundPayment).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Confirm refund" }));
    await screen.findByRole("alert");
    const key = vi.mocked(refundPayment).mock.calls[0][1];
    first.unmount();
    show();
    await user.type(await screen.findByRole("textbox", { name: "Refund reason" }), "Customer request");
    await user.click(screen.getByRole("button", { name: "Refund payment" }));
    await user.click(screen.getByRole("button", { name: "Confirm refund" }));
    await waitFor(() => expect(refundPayment).toHaveBeenCalledTimes(2));
    expect(vi.mocked(refundPayment).mock.calls[1]).toEqual(["payment-1", key, "Customer request"]);
    expect(await screen.findByText(/Refund recorded:/)).toBeInTheDocument();
  });

  it("cancel does not submit a refund", async () => {
    const user = userEvent.setup();
    show();
    await user.type(await screen.findByRole("textbox", { name: "Refund reason" }), "Customer request");
    await user.click(screen.getByRole("button", { name: "Refund payment" }));
    await user.click(screen.getByRole("button", { name: "Cancel" }));
    expect(refundPayment).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
