import { useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { fetchReconItems } from "../api/adminClient";
import { formatMoney } from "../../lib/money";

const CLASS_STYLE: Record<string, string> = {
  MATCHED: "text-emerald-600",
  MISMATCHED: "text-amber-600",
  MISSING_INTERNAL: "text-red-500",
  MISSING_EXTERNAL: "text-red-500",
};

export default function ReconciliationPage() {
  const { runId = "" } = useParams();
  const { data: items, isLoading, error } = useQuery({
    queryKey: ["recon-items", runId],
    queryFn: () => fetchReconItems(runId),
  });

  if (error) return <p role="alert">{error.message}</p>;
  if (isLoading) return <p className="p-8 text-sm text-slate-400">Loading…</p>;

  const fmt = (v: number | null | undefined, c: string | null | undefined) =>
    v == null || c == null ? "—" : formatMoney(v, c);

  return (
    <div className="mx-auto max-w-7xl px-6 py-8">
      <h1 className="mb-4 text-2xl font-bold dark:text-white">
        Reconciliation report
      </h1>
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b text-slate-500 dark:border-slate-700">
            <th className="py-2">Processor ref</th><th>Result</th>
            <th className="text-right">Internal amount</th>
            <th className="text-right">External amount</th>
            <th className="text-right">Internal fee</th>
            <th className="text-right">External fee</th>
          </tr>
        </thead>
        <tbody>
          {(items ?? []).map((i) => (
            <tr key={i.processorRef}
              className="border-b dark:border-slate-800">
              <td className="py-2 font-mono text-xs">{i.processorRef}</td>
              <td className={`text-xs font-semibold ${CLASS_STYLE[i.classification ?? ""]}`}>
                {i.classification}
              </td>
              <td className="text-right">{fmt(i.internalAmountMinor, i.internalCurrency)}</td>
              <td className="text-right">{fmt(i.externalAmountMinor, i.externalCurrency)}</td>
              <td className="text-right">{fmt(i.internalFeeMinor, i.internalCurrency)}</td>
              <td className="text-right">{fmt(i.externalFeeMinor, i.externalCurrency)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}