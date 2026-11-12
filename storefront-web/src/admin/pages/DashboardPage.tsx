import { useQuery } from "@tanstack/react-query";
import {
  fetchIntegrity,
  fetchOverview,
  fetchTimeseries,
  fetchRequestTimeseries,
  fetchLedgerTimeseries,
  AdminAuthError,
  type StatusSummary,
  type DeclineReason,
  type TimeseriesBucket,
} from "../api/adminClient";
import { formatMoney } from "../../lib/money";
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from "recharts";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import RiskSection from "../components/RiskSection";

const RANGE_OPTIONS = [
  { label: "24h", hours: 24 },
  { label: "7d", hours: 168 },
  { label: "30d", hours: 720 },
] as const;

export default function DashboardPage() {
  const [rangeHours, setRangeHours] = useState(24);

  const overview = useQuery({
    queryKey: ["admin", "overview"],
    queryFn: fetchOverview,
  });

  const timeseries = useQuery({
    queryKey: ["admin", "timeseries", rangeHours],
    queryFn: () => {
      const now = new Date().toISOString();
      const start = new Date(Date.now() - rangeHours * 3600_000).toISOString();
      return fetchTimeseries(start, now);
    },
  });

  const integrity = useQuery({
    queryKey: ["admin", "integrity"],
    queryFn: fetchIntegrity,
    refetchInterval: 60_000, // check every minute, not every 30s
  });

  const reqTimeseries = useQuery({
    queryKey: ["admin", "req-timeseries", rangeHours],
    queryFn: () => {
      const now = new Date().toISOString();
      const start = new Date(Date.now() - rangeHours * 3600_000).toISOString();
      return fetchRequestTimeseries(start, now);
    },
  });

  const ledgerTimeseries = useQuery({
    queryKey: ["admin", "ledger-timeseries", rangeHours],
    queryFn: () => {
      const now = new Date().toISOString();
      const start = new Date(Date.now() - rangeHours * 3600_000).toISOString();
      return fetchLedgerTimeseries(start, now);
    },
  });

  // Aggregate overview numbers
  const stats = useMemo(() => {
    const byStatus = overview.data?.payments?.byStatus ?? [];
    const total = byStatus.reduce((s: number, r: StatusSummary) => s + r.count, 0);
    const volume = byStatus.reduce(
      (s: number, r: StatusSummary) => s + r.volume_minor_units, 0
    );
    const captured = byStatus.find((r: StatusSummary) => r.status === "CAPTURED");
    const declined = byStatus.find((r: StatusSummary) => r.status === "DECLINED");
    const successRate = total > 0 && captured
      ? ((captured.count / total) * 100).toFixed(1)
      : "—";

    return { total, volume, captured, declined, successRate };
  }, [overview.data]);

  // Shape timeseries for the bar chart — group by hour
  const chartData = useMemo(() => {
    if (!timeseries.data) return [];
    const byHour = new Map<string, { hour: string; count: number; volume: number }>();
    for (const b of timeseries.data as TimeseriesBucket[]) {
      const hour = new Date(b.window_start).toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
      });
      const existing = byHour.get(b.window_start) ?? { hour, count: 0, volume: 0 };
      existing.count += b.payment_count;
      existing.volume += b.volume_minor_units;
      byHour.set(b.window_start, existing);
    }
    return [...byHour.values()];
  }, [timeseries.data]);

  const topDeclines = overview.data?.payments?.topDeclines ?? [];
  const traffic = overview.data?.traffic;
  const ledger = overview.data?.ledger;
  const lag = overview.data?.health?.lag;
  const metricsStatus = overview.data?.health?.status;

  // Hits over time — sum all status classes per hour
  const trafficChartData = useMemo(() => {
    if (!reqTimeseries.data) return [];
    const byHour = new Map<string, { hour: string; hits: number; errors: number }>();
    for (const b of reqTimeseries.data) {
      const hour = new Date(b.window_start).toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
      });
      const existing = byHour.get(b.window_start) ?? { hour, hits: 0, errors: 0 };
      existing.hits += b.hit_count;
      if (b.status_class === "5xx") existing.errors += b.hit_count;
      byHour.set(b.window_start, existing);
    }
    return [...byHour.values()];
  }, [reqTimeseries.data]);

  const ledgerChartData = useMemo(() => {
    if (!ledgerTimeseries.data) return [];
    return ledgerTimeseries.data.map((r) => ({
      hour: new Date(r.window_start).toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
      }),
      transactions: r.transaction_count,
    }));
  }, [ledgerTimeseries.data]);

  const debits = Number(ledger?.total_debits ?? 0);
  const credits = Number(ledger?.total_credits ?? 0);
  const ledgerBalanced = debits === credits;

  const isAuthError =
    overview.error instanceof AdminAuthError ||
    timeseries.error instanceof AdminAuthError;

  if (isAuthError) {
    return (
      <div className="flex items-center justify-center py-20">
        <p className="text-red-500">
          Invalid admin token. Refresh and try again.
        </p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl px-6 py-8">
      <h1 className="mb-8 text-2xl font-bold text-slate-900 dark:text-white">
        Dashboard
      </h1>

      {metricsStatus?.stale && (
        <div className="mb-8 rounded-lg border border-amber-300 bg-amber-50 px-4 py-2 text-sm text-amber-800 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-300">
          Metrics stale — last event processed{" "}
          {metricsStatus.lastProcessedAt
            ? new Date(metricsStatus.lastProcessedAt).toLocaleTimeString()
            : "never"}
        </div>
      )}

      {/* KPI Cards */}
      <div className="mb-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <KpiCard
          label="Total Payments"
          value={String(stats.total)}
          loading={overview.isLoading}
        />
        <KpiCard
          label="Volume"
          value={formatMoney(stats.volume, "USD")}
          loading={overview.isLoading}
        />
        <KpiCard
          label="Success Rate"
          value={`${stats.successRate}%`}
          loading={overview.isLoading}
        />
        <KpiCard
          label="Declined"
          value={String(stats.declined?.count ?? 0)}
          loading={overview.isLoading}
        />
      </div>

      {/* Traffic */}
      <div className="mb-8 rounded-xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
        <div className="mb-4 flex items-center justify-between">
          <div>
            <h2 className="text-sm font-semibold text-slate-700 dark:text-slate-300">
              Traffic
            </h2>
            <p className="text-xs text-slate-400">
              Hourly buckets · times shown in{" "}
              {Intl.DateTimeFormat().resolvedOptions().timeZone}
            </p>
          </div>
          <Link
            to="/traffic"
            className="text-xs font-medium text-indigo-500 hover:text-indigo-400"
          >
            Details →
          </Link>
        </div>

        <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <KpiCard
            label="Error rate"
            value={
              traffic ? `${(traffic.errorRate * 100).toFixed(2)}%` : "—"
            }
            loading={overview.isLoading}
          />
          <KpiCard
            label="p50 latency"
            value={traffic && !Number.isNaN(traffic.p50ms)
              ? `${Math.round(traffic.p50ms)} ms` : "—"}
            loading={overview.isLoading}
          />
          <KpiCard
            label="p95 latency"
            value={traffic && !Number.isNaN(traffic.p95ms)
              ? `${Math.round(traffic.p95ms)} ms` : "—"}
            loading={overview.isLoading}
          />
          <KpiCard
            label="p99 latency"
            value={traffic && !Number.isNaN(traffic.p99ms)
              ? `${Math.round(traffic.p99ms)} ms` : "—"}
            loading={overview.isLoading}
          />
        </div>

        {reqTimeseries.isLoading ? (
          <div className="flex h-48 items-center justify-center text-sm text-slate-400">
            Loading chart...
          </div>
        ) : trafficChartData.length === 0 ? (
          <div className="flex h-48 items-center justify-center text-sm text-slate-400">
            No traffic data for this range
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={trafficChartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="hour" tick={{ fontSize: 11 }} stroke="#94a3b8" />
              <YAxis allowDecimals={false} tick={{ fontSize: 11 }} stroke="#94a3b8" />
              <Tooltip
                contentStyle={{
                  backgroundColor: "#1e293b",
                  border: "none",
                  borderRadius: 8,
                  color: "#f8fafc",
                  fontSize: 12,
                }}
              />
              <Bar dataKey="hits" fill="#0ea5e9" radius={[4, 4, 0, 0]} name="Requests" />
            </BarChart>
          </ResponsiveContainer>
        )}

        {(traffic?.byRoute?.length ?? 0) > 0 && (
          <ul className="mt-6 space-y-2">
            {traffic!.byRoute.slice(0, 5).map((r) => (
              <li
                key={r.route}
                className="flex items-center justify-between rounded-lg bg-slate-50 px-4 py-2 dark:bg-slate-800/50"
              >
                <span className="font-mono text-xs text-slate-600 dark:text-slate-300">
                  {r.route}
                </span>
                <span className="text-xs font-semibold text-slate-500 dark:text-slate-400">
                  {r.hit_count}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Timeseries Chart */}
      <div className="mb-8 rounded-xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
        <div className="mb-4 flex items-center justify-between">
          <div>
            <h2 className="text-sm font-semibold text-slate-700 dark:text-slate-300">
              Payment Volume
            </h2>
            <p className="text-xs text-slate-400">
              Hourly buckets · times shown in{" "}
              {Intl.DateTimeFormat().resolvedOptions().timeZone}
            </p>
            <Link
              to="/payments"
              className="text-xs font-medium text-indigo-500 hover:text-indigo-400"
            >
              Details →
            </Link>
          </div>
          <div className="flex gap-1">
            {RANGE_OPTIONS.map((opt) => (
              <button
                key={opt.hours}
                onClick={() => setRangeHours(opt.hours)}
                className={`rounded px-3 py-1 text-xs font-medium transition ${
                  rangeHours === opt.hours
                    ? "bg-indigo-600 text-white"
                    : "bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-400"
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>

        {timeseries.isLoading ? (
          <div className="flex h-64 items-center justify-center text-sm text-slate-400">
            Loading chart...
          </div>
        ) : chartData.length === 0 ? (
          <div className="flex h-64 items-center justify-center text-sm text-slate-400">
            No data for this range
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="hour" tick={{ fontSize: 11 }} stroke="#94a3b8" />
              <YAxis allowDecimals={false} tick={{ fontSize: 11 }} stroke="#94a3b8" />
              <Tooltip
                contentStyle={{
                  backgroundColor: "#1e293b",
                  border: "none",
                  borderRadius: 8,
                  color: "#f8fafc",
                  fontSize: 12,
                }}
              />
              <Bar dataKey="count" fill="#6366f1" radius={[4, 4, 0, 0]} name="Payments" />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>

      {/* Top Decline Reasons */}
      <div className="rounded-xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-700 dark:text-slate-300">
            Top Decline Reasons
          </h2>
          <Link
            to="/payments"
            className="text-xs font-medium text-indigo-500 hover:text-indigo-400"
          >
            Details →
          </Link>
        </div>
        {topDeclines.length === 0 ? (
          <p className="text-sm text-slate-400">No declines recorded</p>
        ) : (
          <ul className="space-y-2">
            {topDeclines.map((d: DeclineReason) => (
              <li
                key={d.decline_reason}
                className="flex items-center justify-between rounded-lg bg-slate-50 px-4 py-2.5 dark:bg-slate-800/50"
              >
                <span className="text-sm text-slate-600 dark:text-slate-300">
                  {d.decline_reason}
                </span>
                <span className="rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-semibold text-red-700 dark:bg-red-500/10 dark:text-red-400">
                  {d.count}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <RiskSection
        risk={overview.data?.risk}
        loading={overview.isLoading}
        error={overview.isError}
      />

      {/* Ledger & Health */}
      <div className="mt-8 rounded-xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-700 dark:text-slate-300">
            Ledger &amp; Health
          </h2>
          <Link
            to="/ledger"
            className="text-xs font-medium text-indigo-500 hover:text-indigo-400"
          >
            Details →
          </Link>
        </div>

        <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <KpiCard
            label="Transactions posted"
            value={String(ledger?.transaction_count ?? 0)}
            loading={overview.isLoading}
          />
          <KpiCard
            label="Entries written"
            value={String(ledger?.entry_count ?? 0)}
            loading={overview.isLoading}
          />
          <div className="rounded-xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
            <p className="mb-1 text-xs font-medium uppercase tracking-wider text-slate-400">
              Trial balance
            </p>
            {overview.isLoading ? (
              <div className="h-8 w-24 animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
            ) : (
              <p
                className={`text-2xl font-bold ${
                  ledgerBalanced
                    ? "text-emerald-600 dark:text-emerald-400"
                    : "text-red-600 dark:text-red-400"
                }`}
              >
                {ledgerBalanced ? "Balanced" : "UNBALANCED"}
              </p>
            )}
            {!overview.isLoading && (
              <p className="mt-1 text-xs text-slate-400">
                debits {debits} / credits {credits}
              </p>
            )}
          </div>
          <KpiCard
            label="Consumer lag"
            value={
              lag?.totalLag === undefined || lag.totalLag < 0
                ? "unknown"
                : String(lag.totalLag)
            }
            loading={overview.isLoading}
          />
        </div>

        {ledgerTimeseries.isLoading ? (
          <div className="flex h-48 items-center justify-center text-sm text-slate-400">
            Loading chart...
          </div>
        ) : ledgerChartData.length === 0 ? (
          <div className="flex h-48 items-center justify-center text-sm text-slate-400">
            No ledger activity in this range
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={ledgerChartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="hour" tick={{ fontSize: 11 }} stroke="#94a3b8" />
              <YAxis allowDecimals={false} tick={{ fontSize: 11 }} stroke="#94a3b8" />
              <Tooltip
                contentStyle={{
                  backgroundColor: "#1e293b",
                  border: "none",
                  borderRadius: 8,
                  color: "#f8fafc",
                  fontSize: 12,
                }}
              />
              <Bar
                dataKey="transactions"
                fill="#10b981"
                radius={[4, 4, 0, 0]}
                name="Transactions"
              />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>

      {/* Ledger Integrity */}
      <div className="mt-8 rounded-xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-700 dark:text-slate-300">
            Ledger Integrity
          </h2>
          <Link
            to="/ledger"
            className="text-xs font-medium text-indigo-500 hover:text-indigo-400"
          >
            Details →
          </Link>
        </div>
        {integrity.isLoading ? (
          <div className="h-12 w-48 animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
        ) : integrity.data?.healthy ? (
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-emerald-100 dark:bg-emerald-500/10">
              <svg className="h-5 w-5 text-emerald-600 dark:text-emerald-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
                <path d="m5 12 5 5L20 7" />
              </svg>
            </span>
            <div>
              <p className="text-sm font-medium text-emerald-700 dark:text-emerald-400">
                All balances consistent
              </p>
              <p className="text-xs text-slate-400">
                {integrity.data.accountsChecked} accounts checked
              </p>
            </div>
          </div>
        ) : (
          <div>
            <div className="mb-3 flex items-center gap-3">
              <span className="flex h-10 w-10 items-center justify-center rounded-full bg-red-100 dark:bg-red-500/10">
                <svg className="h-5 w-5 text-red-600 dark:text-red-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
                  <path d="M12 9v4m0 4h.01M10.3 3.2 1.8 18a2 2 0 0 0 1.7 3h16.9a2 2 0 0 0 1.8-3L13.7 3.2a2 2 0 0 0-3.4 0Z" />
                </svg>
              </span>
              <p className="text-sm font-medium text-red-700 dark:text-red-400">
                Drift detected in {integrity.data?.driftedAccounts.length} account(s)
              </p>
            </div>
            <ul className="space-y-1">
              {integrity.data?.driftedAccounts.map((a) => (
                <li key={a.accountId} className="rounded bg-red-50 px-3 py-2 text-xs dark:bg-red-500/5">
                  <span className="font-medium">{a.accountRef}</span>
                  : stored={a.stored} vs recomputed={a.recomputed}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}

function KpiCard({
  label,
  value,
  loading,
}: {
  label: string;
  value: string;
  loading: boolean;
}) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
      <p className="mb-1 text-xs font-medium uppercase tracking-wider text-slate-400">
        {label}
      </p>
      {loading ? (
        <div className="h-8 w-24 animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
      ) : (
        <p className="text-2xl font-bold text-slate-900 dark:text-white">
          {value}
        </p>
      )}
    </div>
  );
}