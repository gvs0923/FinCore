import { useQuery } from "@tanstack/react-query";
import {
  fetchIntegrity,
  fetchOverview,
  fetchTimeseries,
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

      {/* Timeseries Chart */}
      <div className="mb-8 rounded-xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-700 dark:text-slate-300">
            Payment Volume
          </h2>
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
        <h2 className="mb-4 text-sm font-semibold text-slate-700 dark:text-slate-300">
          Top Decline Reasons
        </h2>
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

      {/* Ledger Integrity */}
      <div className="mt-8 rounded-xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
        <h2 className="mb-4 text-sm font-semibold text-slate-700 dark:text-slate-300">
          Ledger Integrity
        </h2>
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