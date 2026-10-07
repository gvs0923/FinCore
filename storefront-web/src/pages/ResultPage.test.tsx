import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { http, HttpResponse } from "msw";
import { server } from "../test/mocks/server";
import ResultPage from "./ResultPage";

const SESSION_ID = "test-session-123";
const PAYMENT_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/result/${SESSION_ID}`]}>
        <Routes>
          <Route path="/result/:sessionId" element={<ResultPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ResultPage", () => {
  it("shows processing state while waiting for the API", async () => {
    // Return PROCESSING so the page shows the spinner text
    server.use(
      http.get("/v1/checkout/sessions/:id", () => {
        return HttpResponse.json({
          sessionId: SESSION_ID,
          amount: 5000,
          currency: "USD",
          status: "PROCESSING",
        });
      }),
    );

    renderPage();

    expect(
      await screen.findByText("Processing your payment…"),
    ).toBeInTheDocument();
  });

  it("shows success state with payment ID", async () => {
    // Default handler returns SUCCEEDED — just render
    renderPage();

    expect(
      await screen.findByText("Payment succeeded"),
    ).toBeInTheDocument();
    expect(screen.getByText(`Payment ID: ${PAYMENT_ID}`)).toBeInTheDocument();
  });

  it("shows failure state with reason", async () => {
    server.use(
      http.get("/v1/checkout/sessions/:id", () => {
        return HttpResponse.json({
          sessionId: SESSION_ID,
          amount: 5000,
          currency: "USD",
          status: "FAILED",
          failureReason: "Card declined",
        });
      }),
    );

    renderPage();

    expect(await screen.findByText("Payment failed")).toBeInTheDocument();
    expect(screen.getByText("Card declined")).toBeInTheDocument();
  });

  it("shows 'Unknown reason' when failure has no reason", async () => {
    server.use(
      http.get("/v1/checkout/sessions/:id", () => {
        return HttpResponse.json({
          sessionId: SESSION_ID,
          amount: 5000,
          currency: "USD",
          status: "FAILED",
        });
      }),
    );

    renderPage();

    expect(await screen.findByText("Payment failed")).toBeInTheDocument();
    expect(screen.getByText("Unknown reason")).toBeInTheDocument();
  });

  it("shows error state when the API returns a network error", async () => {
    server.use(
      http.get("/v1/checkout/sessions/:id", () => {
        return HttpResponse.json(
          { detail: "Internal error" },
          { status: 500 },
        );
      }),
    );

    renderPage();

    expect(
      await screen.findByText("Could not load session."),
    ).toBeInTheDocument();
  });
});
