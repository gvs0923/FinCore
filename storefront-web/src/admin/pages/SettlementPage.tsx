import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { fetchBatches, createBatch } from "../api/adminClient";
import { formatMoney } from "../../lib/money";
import { useState } from "react";
import { hasRole, useAdminUser } from "../auth/adminUser";

const STATUS_STYLE: Record<string, string> = {
  OPEN: "bg-sky-100 text-sky-700 dark:bg-sky-500/10 dark:text-sky-400",
  RECONCILED: "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400",
  PAID_OUT: "bg-slate-200 text-slate-600 dark:bg-slate-700 dark:text-slate-300",
  RECOVERED: "bg-slate-200 text-slate-600 dark:bg-slate-700 dark:text-slate-300",
};

export default function SettlementPage() {
  const canSettle = hasRole(useAdminUser(), "FINANCE_OPS");
  const qc = useQueryClient();
  const { data: batches, isLoading, error } = useQuery({
    queryKey: ["settlement-batches"],
    queryFn: () => fetchBatches(),
  });

  const [date, setDate] = useState(() => {
    const d = new Date();
    d.setUTCDate(d.getUTCDate() - 1);          // only completed days settle
    return d.toISOString().slice(0, 10);
  });

  const create = useMutation({
    mutationFn: () => createBatch("demo-merchant", "USD", date),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["settlement-batches"] }),
  });

  return (
    <div className="mx-auto max-w-7xl px-6 py-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
          Settlement
        </h1>
        <div className="flex items-center gap-2">
          <input type="date" value={date}
            onChange={(e) => setDate(e.target.value)}
            className="rounded border px-2 py-1 text-sm dark:bg-slate-800" />
          <button
            onClick={() => create.mutate()}
            disabled={!canSettle || create.isPending}
            className="rounded bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50">
            Create batch
          </button>
        </div>
      </div>

      {create.isError && (
        <p className="mb-4 text-sm text-red-500">
          {(create.error as Error).message}
        </p>
      )}

      {error && <p role="alert">{error.message}</p>}
      {!isLoading && !error && batches?.length === 0 && <p>No settlement batches yet.</p>}
      {isLoading ? (
        <p className="text-sm text-slate-400">Loading…</p>
      ) : (
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b text-slate-500 dark:border-slate-700">
              <th className="py-2">Date</th><th>Merchant</th>
              <th>Status</th><th className="text-right">Captures</th>
              <th className="text-right">Gross</th><th className="text-right">Fees</th>
              <th className="text-right">Refunds</th><th className="text-right">Net</th>
            </tr>
          </thead>
          <tbody>
            {(batches ?? []).map((b) => (
              <tr key={b.id}
                className="border-b hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900">
                <td className="py-2">
                  <Link to={`/settlement/${b.id}`}
                    className="text-indigo-600 hover:underline dark:text-indigo-400">
                    {b.settlementDate}
                  </Link>
                </td>
                <td>{b.merchantId}
                  <span className="block text-xs text-slate-500">
                    {b.batchKind === "REFUND_ADJUSTMENT" ? "Refund adjustment" : "Daily settlement"}
                  </span>
                </td>
                <td>
                  <span className={`rounded px-2 py-0.5 text-xs font-semibold ${STATUS_STYLE[b.status]}`}>
                    {b.status}
                  </span>
                </td>
                <td className="text-right">{b.captureCount}</td>
                <td className="text-right">{formatMoney(b.grossMinor, b.currency)}</td>
                <td className="text-right">{formatMoney(b.feesMinor, b.currency)}</td>
                <td className="text-right">{formatMoney(b.refundsMinor, b.currency)}</td>
                <td className={`text-right font-medium ${b.netMinor < 0 ? "text-red-500" : ""}`}>
                  {formatMoney(b.netMinor, b.currency)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}