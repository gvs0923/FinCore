import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { http, HttpResponse } from "msw";
import { server } from "../test/mocks/server";
import CheckoutPage from "./CheckoutPage";

const SESSION_ID = "test-session-123";

function renderPage() {
  function ResultSentinel() {
    return <p>result reached</p>;
  }

  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/checkout/${SESSION_ID}`]}>
        <Routes>
          <Route path="/checkout/:sessionId" element={<CheckoutPage />} />
          <Route path="/result/:sessionId" element={<ResultSentinel />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

/** Fills the checkout form with valid test card data. */
async function fillValidCard(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("Card number"), "4242424242424242");
  await user.type(screen.getByLabelText("Expiry (MM/YY)"), "12/28");
  await user.type(screen.getByLabelText("CVV"), "123");
}

describe("CheckoutPage", () => {
  it("renders the form with card number, expiry, CVV, and Pay button", () => {
    renderPage();

    expect(screen.getByLabelText("Card number")).toBeInTheDocument();
    expect(screen.getByLabelText("Expiry (MM/YY)")).toBeInTheDocument();
    expect(screen.getByLabelText("CVV")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /pay/i })).toBeEnabled();
  });

  it("shows test card notice", () => {
    renderPage();
    expect(screen.getByText(/test cards only/i)).toBeInTheDocument();
  });

  it("navigates to result page on successful payment", async () => {
    const user = userEvent.setup();
    renderPage();

    await fillValidCard(user);
    await user.click(screen.getByRole("button", { name: /pay/i }));

    expect(await screen.findByText("result reached")).toBeInTheDocument();
  });

  it("shows validation error for invalid card number (fails Luhn)", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText("Card number"), "4242424242424241");
    await user.type(screen.getByLabelText("Expiry (MM/YY)"), "12/28");
    await user.type(screen.getByLabelText("CVV"), "123");
    await user.click(screen.getByRole("button", { name: /pay/i }));

    expect(await screen.findByText("Card number is invalid")).toBeInTheDocument();
  });

  it("shows validation error for bad expiry format", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText("Card number"), "4242424242424242");
    await user.type(screen.getByLabelText("Expiry (MM/YY)"), "1328");
    await user.type(screen.getByLabelText("CVV"), "123");
    await user.click(screen.getByRole("button", { name: /pay/i }));

    expect(await screen.findByText("Expiry must be MM/YY")).toBeInTheDocument();
  });

  it("shows server error when payment API fails", async () => {
    server.use(
      http.post("/v1/checkout/sessions/:id/pay", () => {
        return HttpResponse.json(
          { detail: "Insufficient funds" },
          { status: 422 },
        );
      }),
    );

    const user = userEvent.setup();
    renderPage();

    await fillValidCard(user);
    await user.click(screen.getByRole("button", { name: /pay/i }));

    expect(await screen.findByText("Insufficient funds")).toBeInTheDocument();
  });
});
