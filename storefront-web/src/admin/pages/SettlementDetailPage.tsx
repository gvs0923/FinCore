import { useParams, Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { parseProcessorCsv } from "../api/processorCsv";
import {
  fetchBatch, fetchBatchItems, fetchReconRuns,
  importReport, payoutBatch,
} from "../api/adminClient";
import { formatMoney } from "../../lib/money";

export default function SettlementDetailPage() {
  const { batchId = "" } = useParams();
  const qc = useQueryClient();
  const [reportJson, setReportJson] = useState("");
  const [reportRef, setReportRef] = useState("");

  const { data: batch, error: batchError } = useQuery({
    queryKey: ["batch", batchId],
    queryFn: () => fetchBatch(batchId),
  });
  const { data: items, error: itemsError } = useQuery({
    queryKey: ["batch-items", batchId],
    queryFn: () => fetchBatchItems(batchId),
  });
  const { data: runs, error: runsError } = useQuery({
    queryKey: ["recon-runs", batchId],
    queryFn: () => fetchReconRuns(batchId),
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["batch", batchId] });
    qc.invalidateQueries({ queryKey: ["recon-runs", batchId] });
  };

  const reconcile = useMutation({
    mutationFn: () => {
      const rows = parseProcessorCsv(reportJson);  // throws → mutation error
      if (!reportRef.trim()) throw new Error("Report reference is required");
      return importReport(batchId, reportRef.trim(), rows);
    },
    onSuccess: invalidate,
  });

  const payout = useMutation({
    mutationFn: () => payoutBatch(batchId),
    onSuccess: invalidate,
  });

  if (batchError) return <p role="alert">{batchError.message}</p>;
  if (!batch) return <p className="p-8 text-sm text-slate-400">Loading…</p>;

  const fields: [string, number][] = [
    ["Gross", batch.grossMinor],
    ["Fees", batch.feesMinor],
    ["Refunds", batch.refundsMinor],
    ["Net", batch.netMinor],
  ];

  return (
    <div className="mx-auto max-w-7xl px-6 py-8">
      <Link to="/settlement" className="text-sm text-indigo-500">← Batches</Link>
      <h1 className="mt-2 text-2xl font-bold dark:text-white">
        {batch.settlementDate} · {batch.merchantId}
        <span className="ml-3 align-middle text-sm">{batch.status}</span>
      </h1>

      <div className="mt-4 grid grid-cols-4 gap-4">
        {fields.map(([label, v]) => (
          <div key={label}
            className="rounded-lg border p-4 dark:border-slate-700">
            <div className="text-xs uppercase text-slate-500">{label}</div>
            <div className={`mt-1 text-xl font-semibold dark:text-white
                ${v < 0 ? "text-red-500" : ""}`}>
              {formatMoney(v, batch.currency)}
            </div>
          </div>
        ))}
      </div>

      {/* Pay out — only meaningful on a reconciled batch */}
      <div className="mt-6 flex gap-3">
        <button
          onClick={() => payout.mutate()}
          disabled={batch.status !== "RECONCILED" || payout.isPending}
          title={batch.status !== "RECONCILED"
            ? "Batch must be RECONCILED first" : "Simulate payout"}
          className="rounded bg-emerald-600 px-4 py-2 text-sm font-medium
                     text-white disabled:cursor-not-allowed disabled:opacity-40">
          Pay out
        </button>
      </div>
      {payout.isError &&
        <p className="mt-2 text-sm text-red-500">{(payout.error as Error).message}</p>}

      {itemsError && <p role="alert">Captures unavailable: {itemsError.message}</p>}
      {runsError && <p role="alert">Reconciliation runs unavailable: {runsError.message}</p>}
      {/* Items */}
      <h2 className="mt-8 mb-2 font-semibold dark:text-white">Captures</h2>
      <table className="w-full text-left text-sm">
        <thead><tr className="border-b text-slate-500 dark:border-slate-700">
          <th className="py-2">Processor ref</th>
          <th className="text-right">Amount</th>
          <th className="text-right">Fee</th>
          <th>Policy</th>
        </tr></thead>
        <tbody>
          {(items ?? []).map((i) => (
            <tr key={i.paymentId} className="border-b dark:border-slate-800">
              <td className="py-2 font-mono text-xs">{i.processorRef}</td>
              <td className="text-right">{formatMoney(i.amountMinor, batch.currency)}</td>
              <td className="text-right">{formatMoney(i.feeMinor, batch.currency)}</td>
              <td className="text-xs text-slate-500">{i.feePolicyVersion}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* Reconciliation */}
      <h2 className="mt-8 mb-2 font-semibold dark:text-white">
        Reconciliation
      </h2>
      <label className="block">Report reference
        <input value={reportRef} onChange={e => setReportRef(e.target.value)} maxLength={255}
          className="ml-3 rounded border p-2" />
      </label>
      <label className="block">Processor CSV
      <textarea
        value={reportJson}
        onChange={(e) => setReportJson(e.target.value)}
        rows={6}
        placeholder={'processorRef,currency,amountMinor,feeMinor\ncapture-example,USD,10000,320'}
        className="w-full rounded border p-2 font-mono text-xs dark:border-slate-700 dark:bg-slate-900" />
      </label>
      <button
        onClick={() => reconcile.mutate()}
        disabled={batch.status !== "OPEN" || reconcile.isPending}
        className="mt-2 rounded bg-indigo-600 px-4 py-2 text-sm font-medium
                   text-white disabled:opacity-40">
        Import processor report
      </button>
      {reconcile.isError &&
        <p className="mt-2 text-sm text-red-500">{(reconcile.error as Error).message}</p>}

      {reconcile.data && (
        <p className="mt-2 text-sm dark:text-slate-300">
          Run {reconcile.data.reportRef}: {reconcile.data.matchedCount} matched,
          {reconcile.data.mismatchedCount} mismatched,
          {reconcile.data.missingInternalCount} missing internal,
          {reconcile.data.missingExternalCount} missing external
          {reconcile.data.breakDetected && (
            <span className="ml-2 font-semibold text-red-500">· BREAK</span>)}
        </p>
      )}

      <h3 className="mt-6 mb-2 text-sm font-semibold dark:text-white">Runs</h3>
      <ul className="text-sm">
        {(runs ?? []).map((r) => (
          <li key={r.id} className="py-1">
            <Link to={`/settlement/reconciliation/${r.id}`}
              className="text-indigo-500 hover:underline">
              {r.reportRef}
            </Link>{" "}
            — {r.itemCount} items
            {r.breakDetected &&
              <span className="ml-2 text-red-500">break detected</span>}
          </li>
        ))}
      </ul>
    </div>
  );
}