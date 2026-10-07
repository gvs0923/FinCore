import { http, HttpResponse } from "msw";
import type { SessionResponse } from "../../api/client";

/**
 * Default MSW handlers for the checkout BFF API.
 *
 * Tests import these and can override individual routes via
 * `server.use(...)` for error / edge-case scenarios.
 */

const SESSION_ID = "test-session-123";
const PAYMENT_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

export const defaultSession: SessionResponse = {
  sessionId: SESSION_ID,
  amount: 5000,
  currency: "USD",
  description: "Premium Widget",
  status: "OPEN",
};

export const handlers = [
  // POST /v1/checkout/sessions — create a checkout session
  http.post("/v1/checkout/sessions", () => {
    return HttpResponse.json(
      { ...defaultSession, status: "OPEN" } satisfies SessionResponse,
      { status: 201 },
    );
  }),

  // POST /v1/checkout/sessions/:id/pay — pay for a session
  http.post("/v1/checkout/sessions/:id/pay", () => {
    return HttpResponse.json({
      ...defaultSession,
      status: "SUCCEEDED",
      paymentId: PAYMENT_ID,
    } satisfies SessionResponse);
  }),

  // GET /v1/checkout/sessions/:id — poll session status
  http.get("/v1/checkout/sessions/:id", () => {
    return HttpResponse.json({
      ...defaultSession,
      status: "SUCCEEDED",
      paymentId: PAYMENT_ID,
    } satisfies SessionResponse);
  }),
];
