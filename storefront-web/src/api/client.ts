import type { components } from "./schema";
import { traceHeaders, traceIdOf } from "../lib/tracing";

export type SessionResponse = components["schemas"]["SessionResponse"];

export class ApiError extends Error {
  readonly traceId: string;

  constructor(message: string, traceId: string) {
    super(message);
    this.name = "ApiError";
    this.traceId = traceId;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const trace = traceHeaders();
  const res = await fetch(path, {
    ...init,
    headers: { "Content-Type": "application/json", ...trace, ...init?.headers },
  });
  if (!res.ok) {
    const problem = await res.json().catch(() => null);
    throw new ApiError(problem?.detail ?? `Request failed: ${res.status}`, traceIdOf(trace));
  }
  return res.json();
}

export function createSession(amount: number, currency: string, description: string) {
  return request<SessionResponse>("/v1/checkout/sessions", {
    method: "POST",
    body: JSON.stringify({ amount, currency, description }),
  });
}

export type PaySignals = Pick<
  components["schemas"]["PaySessionRequest"],
  "deviceId" | "context"
>;
export function paySession(sessionId: string, cardNumber: string, cardBrand: string,
                           cardLast4: string, signals: PaySignals) {
  return request<SessionResponse>(`/v1/checkout/sessions/${sessionId}/pay`, {
    method: "POST",
    body: JSON.stringify({ cardNumber, cardBrand, cardLast4, ...signals }),
  });
}

export function getSession(sessionId: string) {
  return request<SessionResponse>(`/v1/checkout/sessions/${sessionId}`);
}