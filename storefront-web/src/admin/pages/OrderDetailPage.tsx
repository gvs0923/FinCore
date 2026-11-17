import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import { fetchOrder, fetchJournal, refundPayment } from "../api/adminClient";
import { formatMoney } from "../../lib/money";
import { formatLedgerAmount } from "../api/ledgerMoney";

export default function OrderDetailPage() {
  const { paymentId = "" } = useParams();
  return <OrderDetail key={paymentId} paymentId={paymentId} />;
}

function OrderDetail({ paymentId }: { paymentId: string }) {
  const [reason, setReason] = useState("");
  const [confirming, setConfirming] = useState(false);
  const order = useQuery({ queryKey: ["order", paymentId], queryFn: () => fetchOrder(paymentId) });
  const journal = useQuery({ queryKey: ["order-journal", paymentId],
    queryFn: () => fetchJournal(`payment:${paymentId}`), enabled: order.data?.status === "CAPTURED", retry: false });
  const refund = useMutation({ mutationFn: async () => {
    const storageKey = `fincore-refund:${paymentId}`;
    const stored = sessionStorage.getItem(storageKey);
    const attempt: { key: string; reason: string } = stored
      ? JSON.parse(stored) : { key: crypto.randomUUID(), reason: reason.trim() };
    sessionStorage.setItem(storageKey, JSON.stringify(attempt));
    return refundPayment(paymentId, attempt.key, attempt.reason);
  }, onSuccess: () => setConfirming(false) });
  const reversal = useQuery({ queryKey: ["refund-journal", refund.data?.id],
    queryFn: () => fetchJournal(`refund:${refund.data!.id}`), enabled: !!refund.data,
    retry: false });

  if (order.isPending) return <p role="status">Loading order…</p>;
  if (order.isError) return <p role="alert">{order.error.message}</p>;
  const payment = order.data;
  return <section className="mx-auto max-w-7xl space-y-4 px-6 py-8">
    <Link to="/payments" className="text-indigo-500 underline">Back to orders</Link>
    <h1 className="text-2xl font-bold">Order {payment.merchantRef}</h1>
    <p>{payment.status} · {formatMoney(payment.amount, payment.currency)}</p>
    <p>Payment {payment.id} · {payment.cardBrand} ending {payment.cardLast4}</p>
    {payment.declineReason && <p>{payment.declineReason}</p>}
    <h2 className="text-xl font-semibold">Capture journal</h2>
    {journal.isFetching && <p role="status">Loading journal…</p>}
    {journal.isError && <p role="alert">Journal unavailable: {journal.error.message}</p>}
    {journal.data && <ul>{journal.data.entries.map(entry => <li key={entry.entryId}>
      {entry.entryType} {formatLedgerAmount(entry.amount, entry.currency)} · {entry.reference}
    </li>)}</ul>}
    {payment.status === "CAPTURED" && !refund.isSuccess && <div className="space-y-3">
      <p>Full refunds only. Frozen settlement batches require an adjustment and cannot be refunded here.</p>
      <label className="block">Refund reason
        <input className="ml-3 rounded border p-2" value={reason} maxLength={1000}
          disabled={refund.isPending || confirming} onChange={e => setReason(e.target.value)} />
      </label>
      <button disabled={!reason.trim()} onClick={() => setConfirming(true)}>Refund payment</button>
      {confirming && <div role="dialog" aria-modal="true" aria-label="Confirm full refund" className="rounded border p-4">
        <p>Confirm full refund of {formatMoney(payment.amount, payment.currency)}? Retries reuse the same refund attempt.</p>
        <button disabled={refund.isPending} onClick={() => refund.mutate()}>{refund.isPending ? "Refunding…" : "Confirm refund"}</button>
        <button disabled={refund.isPending} onClick={() => setConfirming(false)}>Cancel</button>
      </div>}
    </div>}
    {refund.isError && <p role="alert">{refund.error.message}. Retrying is safe.</p>}
    {refund.data && <p role="status">Refund recorded: {refund.data.id}. Ledger reversal processes asynchronously.</p>}
    {reversal.isError && <p>Reversal not available yet; the journal will refresh automatically.</p>}
    {reversal.data && <ul>{reversal.data.entries.map(entry => <li key={entry.entryId}>
      {entry.entryType} {formatLedgerAmount(entry.amount, entry.currency)}
    </li>)}</ul>}
  </section>;
}
