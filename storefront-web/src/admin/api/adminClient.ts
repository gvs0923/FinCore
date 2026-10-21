const ADMIN_TOKEN_KEY = "fincore-admin-token";

export function getAdminToken(): string {
  return localStorage.getItem(ADMIN_TOKEN_KEY) ?? "";
}

export function setAdminToken(token: string) {
  localStorage.setItem(ADMIN_TOKEN_KEY, token);
}

async function adminRequest<T>(path: string): Promise<T> {
  const res = await fetch(path, {
    headers: {
      "Content-Type": "application/json",
      "X-Admin-Token": getAdminToken(),
    },
  });
  if (res.status === 401) {
    throw new AdminAuthError();
  }
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.message ?? `Admin request failed: ${res.status}`);
  }
  return res.json();
}

export class AdminAuthError extends Error {
  constructor() {
    super("Admin authentication required");
    this.name = "AdminAuthError";
  }
}

// --- API response types ---

export interface StatusSummary {
  status: string;
  count: number;
  volume_minor_units: number;
}

export interface DeclineReason {
  decline_reason: string;
  count: number;
}

export interface OverviewResponse {
  window: { from: string; to: string };
  payments: {
    byStatus: StatusSummary[];
    topDeclines: DeclineReason[];
  };
}

export interface TimeseriesBucket {
  window_start: string;
  status: string;
  currency: string;
  decline_reason: string;
  payment_count: number;
  volume_minor_units: number;
}

// --- API functions ---

export function fetchOverview() {
  return adminRequest<OverviewResponse>("/v1/admin/overview");
}

export function fetchTimeseries(from: string, to: string) {
  const params = new URLSearchParams({
    metric: "payment_count",
    from,
    to,
    bucket: "hour",
  });
  return adminRequest<TimeseriesBucket[]>(
    `/v1/admin/metrics/timeseries?${params}`
  );
}
export interface IntegrityResult {
  accountId: string;
  accountRef: string;
  recomputed: number;
  stored: number | null;
  drifted: boolean;
}

export interface IntegrityResponse {
  healthy: boolean;
  accountsChecked: number;
  driftedAccounts: IntegrityResult[];
}

export function fetchIntegrity() {
  return adminRequest<IntegrityResponse>("/v1/admin/integrity/balances");
}
