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

export interface RouteHits {
  route: string;
  hit_count: number;
}

export interface TrafficSummary {
  byRoute: RouteHits[];
  errorRate: number;
  p50ms: number;
  p95ms: number;
  p99ms: number;
}

export interface LedgerSummary {
  transaction_count: number;
  entry_count: number;
  total_debits: number;
  total_credits: number;
}

export interface LagInfo {
  totalLag: number;
  error?: string;
}

export interface OverviewResponse {
  window: { from: string; to: string };
  payments: {
    byStatus: StatusSummary[];
    topDeclines: DeclineReason[];
  };
  traffic: TrafficSummary;
  ledger: LedgerSummary;
  health: {
    status: MetricsStatus;
    lag: LagInfo;
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

// One generic endpoint for every chart; `metric` picks the rollup.
function fetchMetric<T>(metric: string, from: string, to: string) {
  const params = new URLSearchParams({ metric, from, to, bucket: "hour" });
  return adminRequest<T[]>(`/v1/admin/metrics/timeseries?${params}`);
}

export function fetchTimeseries(from: string, to: string) {
  return fetchMetric<TimeseriesBucket>("payment_count", from, to);
}

export function fetchRequestTimeseries(from: string, to: string) {
  return fetchMetric<RequestTimeseriesBucket>("request_count", from, to);
}

export function fetchLedgerTimeseries(from: string, to: string) {
  return fetchMetric<LedgerTimeseriesRow>("ledger_transactions", from, to);
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

export interface MetricsStatus {
  lastProcessedAt: string;
  stale: boolean;
}

export interface RequestTimeseriesBucket {
  window_start: string;
  status_class: string;
  hit_count: number;
}

export interface LedgerTimeseriesRow {
  window_start: string;
  transaction_count: number;
  entry_count: number;
  total_debits: number;
  total_credits: number;
}
