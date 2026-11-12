import { describe, it, expect, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { http, HttpResponse } from "msw";
import { server } from "../test/mocks/server";
import ResultPage from "./ResultPage";

const SESSION_ID = "test-session-123";
const PAYMENT_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

// jsdom does not implement EventSource. Provide a minimal mock that
// immediately fires onerror so ResultPage falls back to polling —
// which is exactly the behaviour these tests exercise.
beforeEach(() => {
  class MockEventSource {
    onopen: (() => void) | null = null;
    onerror: (() => void) | null = null;
    close() {}
    addEventListener() {}
    constructor() {
      // Fire onerror on next tick so the component mounts first
      setTimeout(() => this.onerror?.(), 0);
    }
  }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (globalThis as any).EventSource = MockEventSource;
});

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
  it.each([
    ["SUCCEEDED", "Payment succeeded"],
    ["FAILED", "Payment failed"],
  ])("keeps polling after a REVIEW SSE event until %s", async (status, heading) => {
    class ReviewEventSource {
      onerror: (() => void) | null = null;
      close() {}
      addEventListener(name: string, callback: (event: MessageEvent) => void) {
        if (name === "status") {
          setTimeout(() => callback(new MessageEvent("status", {
            data: JSON.stringify({ sessionId: SESSION_ID, status: "REVIEW" }),
          })), 0);
        }
      }
    }
    Object.defineProperty(globalThis, "EventSource", {
      configurable: true, writable: true, value: ReviewEventSource,
    });
    let reads = 0;
    server.use(http.get("/v1/checkout/sessions/:id", () => HttpResponse.json({
      sessionId: SESSION_ID,
      paymentId: PAYMENT_ID,
      amount: 5000,
      currency: "USD",
      status: ++reads === 1 ? "REVIEW" : status,
    })));

    renderPage();
    expect(await screen.findByText("Order pending review")).toBeInTheDocument();
    expect(screen.queryByText("Payment succeeded")).not.toBeInTheDocument();
    expect(await screen.findByText(heading, {}, { timeout: 4000 })).toBeInTheDocument();
    expect(reads).toBeGreaterThanOrEqual(2);
  });
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

  it("shows 'still processing' when SSE times out — never a false success", async () => {
  // Mock EventSource that emits the server's "timeout" event,
  // simulating the broker being down and events never arriving.
  class TimeoutEventSource {
    onerror: (() => void) | null = null;
    close() {}
    addEventListener(name: string, cb: (e: MessageEvent) => void) {
      if (name === "timeout") {
        setTimeout(() => cb({ data: '{"message":"Still processing"}' } as MessageEvent), 0);
      }
    }
  }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (globalThis as any).EventSource = TimeoutEventSource;

  renderPage();

  expect(await screen.findByText("Still processing")).toBeInTheDocument();
  expect(screen.queryByText("Payment succeeded")).not.toBeInTheDocument();
});

it("falls back to polling when SSE errors, and polls to a truthful result", async () => {
  // Default MockEventSource already fires onerror → component polls
  server.use(
    http.get("/v1/checkout/sessions/:id", () =>
      HttpResponse.json({
        sessionId: SESSION_ID,
        amount: 5000,
        currency: "USD",
        status: "PROCESSING",   // broker still dead — honest answer
      }),
    ),
  );

  renderPage();

  // Polling with PROCESSING shows the spinner — not success, not failure
  expect(await screen.findByText("Processing your payment…")).toBeInTheDocument();
  expect(screen.queryByText("Payment succeeded")).not.toBeInTheDocument();
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
