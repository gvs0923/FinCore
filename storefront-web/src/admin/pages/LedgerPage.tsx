import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import {
  fetchLedgerAccounts, fetchAccountEntries, fetchJournal,
} from "../api/adminClient";
import { formatLedgerAmount } from "../api/ledgerMoney";

export default function LedgerPage() {
  const [selected, setSelected] = useState<string | null>(null);
  const [journalRef, setJournalRef] = useState<string | null>(null);

  const { data: accounts } = useQuery({
    queryKey: ["ledger-accounts"], queryFn: fetchLedgerAccounts });
  const { data: entries } = useQuery({
    queryKey: ["ledger-entries", selected],
    queryFn: () => fetchAccountEntries(selected!),
    enabled: !!selected,
  });
  const { data: journal } = useQuery({
    queryKey: ["journal", journalRef],
    queryFn: () => fetchJournal(journalRef!),
    enabled: !!journalRef,
  });

  return (
    <div className="mx-auto grid max-w-7xl grid-cols-[280px_1fr] gap-6 px-6 py-8">
      {/* Chart of accounts */}
      <div>
        <h2 className="mb-2 font-semibold dark:text-white">Accounts</h2>
        <ul className="space-y-1 text-sm">
          {(accounts ?? []).map((a) => (
            <li key={a.accountRef}>
              <button
                onClick={() => setSelected(a.accountRef)}
                className={`w-full rounded px-2 py-1.5 text-left font-mono text-xs
                  ${selected === a.accountRef
                    ? "bg-indigo-100 dark:bg-indigo-500/20"
                    : "hover:bg-slate-100 dark:hover:bg-slate-800"}`}>
                {a.accountRef}
                <span className="float-right text-slate-500">
                  {formatLedgerAmount(a.balance, a.currency)}
                </span>
              </button>
            </li>
          ))}
        </ul>
      </div>

      {/* Entries for the selected account */}
      <div>
        <h2 className="mb-2 font-semibold dark:text-white">
          {selected ?? "Select an account"}
        </h2>
        {selected && (
          <table className="w-full text-left text-sm">
            <thead><tr className="border-b text-slate-500 dark:border-slate-700">
              <th className="py-2">Type</th><th className="text-right">Amount</th>
              <th>Transaction</th><th>When</th>
            </tr></thead>
            <tbody>
              {(entries ?? []).map((e) => (
                <tr key={e.entryId} className="border-b dark:border-slate-800">
                  <td className={`py-1.5 text-xs font-semibold
                    ${e.entryType === "DEBIT" ? "text-indigo-500" : "text-emerald-600"}`}>
                    {e.entryType}
                  </td>
                  <td className="text-right">
                    {formatLedgerAmount(e.amount, e.currency)}
                  </td>
                  <td>
                    <button onClick={() => setJournalRef(e.reference)}
                      className="font-mono text-xs text-indigo-500 hover:underline">
                      {e.reference}
                    </button>
                  </td>
                  <td className="text-xs text-slate-500">
                    {new Date(e.createdAt).toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Journal overlay: the whole transaction */}
      {journal && (
        <div className="fixed inset-0 z-20 flex items-center justify-center bg-black/40"
          onClick={() => setJournalRef(null)}>
          <div className="w-full max-w-lg rounded-lg bg-white p-6 dark:bg-slate-900"
            onClick={(e) => e.stopPropagation()}>
            <h3 className="font-semibold dark:text-white">{journal.description}</h3>
            <p className="mt-1 font-mono text-xs text-slate-500">{journal.reference}</p>
            <ul className="mt-4 space-y-1 font-mono text-xs">
              {journal.entries.map((e) => (
                <li key={e.entryId} className="flex justify-between">
                  <span className={e.entryType === "DEBIT"
                      ? "text-indigo-500" : "text-emerald-600"}>
                    {e.entryType}
                  </span>
                  <span>{formatLedgerAmount(e.amount, e.currency)}</span>
                </li>
              ))}
            </ul>
            <button onClick={() => setJournalRef(null)}
              className="mt-4 text-sm text-slate-500 hover:underline">
              Close
            </button>
          </div>
        </div>
      )}
    </div>
  );
}