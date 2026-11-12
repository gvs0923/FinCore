import type { components } from "./schema";

export type SessionResponse = components["schemas"]["SessionResponse"];

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });
  if (!res.ok) {
    const problem = await res.json().catch(() => null);
    throw new Error(problem?.detail ?? `Request failed: ${res.status}`);
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
  "deviceId"
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