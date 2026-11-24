import type { components } from "../../api/schema";
import { traceHeaders } from "../../lib/tracing";

export type AdminMe = components["schemas"]["AdminMe"];


export type AdminUser = AdminMe;
export const ADMIN_SESSION_EXPIRED = "fincore-admin-session-expired";


function xsrfToken(): string {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : "";
}

async function adminRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": xsrfToken(),
      ...traceHeaders(),
      ...init.headers,
    },
  });
  if (res.status === 401) {
    window.dispatchEvent(new Event(ADMIN_SESSION_EXPIRED));
    throw new AdminAuthError();
  }
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    if (res.status === 403 && body?.code !== "CSRF_TOKEN_INVALID") throw new AdminForbiddenError();
    throw new Error(body?.message ?? body?.detail ?? `Admin request failed: ${res.status}`);
  }
  return res.json();
}

export class AdminAuthError extends Error {
  constructor() {
    super("Admin authentication required");
    this.name = "AdminAuthError";
  }
}

export class AdminForbiddenError extends Error {
  constructor() {
    super("Your role does not permit this action");
    this.name = "AdminForbiddenError";
  }
}

// Where to come back to after the round-trip through Keycloak. sessionStorage
// survives the redirect but not a duplicated tab, which is what we want.
const RETURN_PATH_KEY = "fincore-admin-return";

export function login(): void {
  sessionStorage.setItem(
    RETURN_PATH_KEY,
    window.location.pathname + window.location.search);
  window.location.assign("/oauth2/authorization/keycloak");
}

export function takeReturnPath(): string | null {
  const path = sessionStorage.getItem(RETURN_PATH_KEY);
  sessionStorage.removeItem(RETURN_PATH_KEY);
  return path && /^\/admin(?:\/|\?|$)/.test(path) ? path : null;
}

export function fetchMe() {
  return adminRequest<AdminMe>("/v1/admin/me");
}

// Logout ends the IdP session too; the BFF answers {redirectUrl} instead of a
// 302 so fetch can hand it to the browser. 
export async function logout(): Promise<void> {
  const body = await adminRequest<{ redirectUrl?: string }>("/logout", { method: "POST" });
  window.location.assign(body.redirectUrl ?? "/admin");
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
  risk: RiskSummary;
  settlement: SettlementSummary | null;
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

export type ReviewItem = components["schemas"]["ReviewItem"];

export type RiskSummary = components["schemas"]["RiskSummary"];

export type SettlementSummary = components["schemas"]["SettlementSummary"];

export function fetchReviewQueue() {
  return adminRequest<ReviewItem[]>("/v1/admin/risk/queue");
}

export function resolveReview(paymentId: string, action: "approve" | "reject") {
  return adminRequest<unknown>(
    `/v1/admin/risk/${encodeURIComponent(paymentId)}/resolve?action=${action}`,
    { method: "POST" },
  );
}

// ---------- Settlement & reconciliation ----------

export type SettlementBatch = Required<components["schemas"]["SettlementBatch"]>; // "2026-01-10"

export type BatchItem = Required<components["schemas"]["BatchItem"]>;

export type ReconciliationRun = Required<components["schemas"]["ReconciliationRun"]>;

export type ReconciliationItem = components["schemas"]["ReconciliationItem"];

export type RefundResult = Required<components["schemas"]["RefundResult"]>;

export function fetchBatches(limit = 50, offset = 0) {
  return adminRequest<SettlementBatch[]>(
    `/v1/admin/settlement/batches?limit=${limit}&offset=${offset}`);
}

export function fetchBatch(batchId: string) {
  return adminRequest<SettlementBatch>(
    `/v1/admin/settlement/batches/${batchId}`);
}

export function fetchAdjustments(batchId: string, offset = 0) {
  return adminRequest<SettlementBatch[]>(
    `/v1/admin/settlement/batches/${batchId}/adjustments?limit=50&offset=${offset}`);
}

export function fetchBatchItems(batchId: string) {
  return adminRequest<BatchItem[]>(
    `/v1/admin/settlement/batches/${batchId}/items`);
}

export function createBatch(
  merchantId: string, currency: string, settlementDate: string) {
  const p = new URLSearchParams({ merchantId, currency, settlementDate });
  return adminRequest<SettlementBatch>(
    `/v1/admin/settlement/batches?${p}`, { method: "POST" });
}

export function payoutBatch(batchId: string) {
  return adminRequest<SettlementBatch>(
    `/v1/admin/settlement/batches/${batchId}/payout`,
    { method: "POST" });
}

export function importReport(
  batchId: string, reportRef: string,
  rows: { processorRef: string; currency: string;
          amountMinor: number; feeMinor: number }[]) {
  return adminRequest<ReconciliationRun>(
    `/v1/admin/settlement/batches/${batchId}/reconciliation`,
    { method: "POST", body: JSON.stringify({ reportRef, rows }) });
}

export function fetchReconRuns(batchId: string) {
  return adminRequest<ReconciliationRun[]>(
    `/v1/admin/settlement/batches/${batchId}/reconciliation-runs`);
}

export function fetchReconItems(runId: string) {
  return adminRequest<ReconciliationItem[]>(
    `/v1/admin/settlement/reconciliation-runs/${runId}/items`);
}

export function refundPayment(
  paymentId: string, idempotencyKey: string,
  reason: string) {
  return adminRequest<RefundResult>("/v1/admin/settlement/refunds", {
    method: "POST",
    body: JSON.stringify({ paymentId, idempotencyKey, reason }),
  });
}

// ---------- Ledger explorer ----------

export type LedgerAccount = Required<components["schemas"]["LedgerAccount"]>; // BigDecimal arrives as JSON string/number

export type LedgerEntryRow = Required<components["schemas"]["LedgerEntryRow"]>;

export type LedgerJournal = Omit<Required<components["schemas"]["LedgerJournal"]>, "entries"> & { entries: LedgerEntryRow[] };

export type Order = Required<components["schemas"]["Order"]>;

export function fetchOrders(page = 0) {
  return adminRequest<Order[]>(`/v1/admin/orders?page=${page}`);
}

export function fetchOrder(paymentId: string) {
  return adminRequest<Order>(`/v1/admin/orders/${encodeURIComponent(paymentId)}`);
}

export function fetchLedgerAccounts() {
  return adminRequest<LedgerAccount[]>("/v1/admin/ledger/accounts");
}

export function fetchAccountEntries(accountRef: string) {
  return adminRequest<LedgerEntryRow[]>(
    `/v1/admin/ledger/accounts/${encodeURIComponent(accountRef)}/entries`);
}

export function fetchJournal(reference: string) {
  return adminRequest<LedgerJournal>(
    `/v1/admin/ledger/transactions/${encodeURIComponent(reference)}`);
}
