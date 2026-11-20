import { Link } from "react-router-dom";
import type { SettlementSummary } from "../api/adminClient";
import { formatMoney } from "../../lib/money";

export default function SettlementSection({
  settlement,
  loading,
  error,
}: {
  settlement?: SettlementSummary | null;
  loading: boolean;
  error: boolean;
}) {
  return (
    <section className="my-8 rounded-xl border border-slate-200 p-6 dark:border-slate-800">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="font-semibold">Settlement · last 24 hours</h2>
        <Link to="/settlement" className="text-sm text-indigo-500">
          Batches
        </Link>
      </div>

      {loading ? (
        <p role="status">Loading settlement metrics…</p>
      ) : error || !settlement || !Array.isArray(settlement.batches) ? (
        <p role="alert">Settlement metrics are unavailable.</p>
      ) : (
        <>
          <dl className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <Tile
              label="Refunds"
              value={String(settlement.refundCount)}
              detail={formatMoney(settlement.refundMinor, settlement.currency)}
            />
            <Tile
              label="Paid out"
              value={String(settlement.payoutCount)}
              detail={formatMoney(settlement.payoutMinor, settlement.currency)}
            />
            <Tile
              label="Reconciliation runs"
              value={String(settlement.reconciliationRuns)}
            />
            <Tile
              label="Reconciliation breaks"
              value={String(settlement.reconciliationBreaks)}
              alert={settlement.reconciliationBreaks > 0}
            />
          </dl>

          <h3 className="mb-2 mt-6 text-sm font-medium">
            Batches by state (all time)
          </h3>
          {settlement.batches.length === 0 ? (
            <p className="text-sm text-slate-500">No settlement batches yet.</p>
          ) : (
            <ul className="space-y-2">
              {settlement.batches.map((b) => (
                <li
                  key={`${b.batchKind}:${b.status}`}
                  className="flex justify-between text-sm"
                >
                  <span>
                    {b.batchKind} · {b.status}
                  </span>
                  <span>
                    {b.count} · net {formatMoney(b.netMinor, settlement.currency)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  );
}

function Tile({
  label,
  value,
  detail,
  alert = false,
}: {
  label: string;
  value: string;
  detail?: string;
  alert?: boolean;
}) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd
        className={`text-2xl font-semibold ${alert ? "text-red-600 dark:text-red-400" : ""}`}
      >
        {value}
      </dd>
      {detail && <dd className="text-xs text-slate-500">{detail}</dd>}
    </div>
  );
}
