import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchReviewQueue, resolveReview } from "../api/adminClient";
import { hasRole, useAdminUser } from "../auth/adminUser";

export default function ReviewQueuePage() {
  const canResolve = hasRole(useAdminUser(), "RISK_ANALYST");
  const qc = useQueryClient();
  const { data, isLoading, error } = useQuery({
    queryKey: ["risk-queue"],
    queryFn: fetchReviewQueue,
    refetchInterval: 15_000,
  });
  const resolve = useMutation({
    mutationFn: ({ id, action }: { id: string; action: "approve" | "reject" }) =>
      resolveReview(id, action),
    // Settled, not success: a 409 (already resolved elsewhere) also drains the row
    onSettled: () => qc.invalidateQueries({ queryKey: ["risk-queue"] }),
  });

  function requestResolution(id: string, action: "approve" | "reject") {
    const message =
      action === "approve"
        ? "Approve this payment and capture its authorized amount?"
        : "Reject this payment?";

    if (window.confirm(message)) {
      resolve.mutate({ id, action });
    }
  }

  if (isLoading) return <p className="p-6">Loading…</p>;
  if (error) return <p className="p-6 text-red-500">Failed to load queue.</p>;
  if (!data?.length)
    return <p className="p-6 text-slate-500">Queue is empty — nothing under review.</p>;

  return (
    <div className="mx-auto max-w-5xl px-6 py-8">
      <h1 className="text-xl font-bold text-slate-900 dark:text-white">Risk review queue</h1>
      {resolve.isError && (
        <p role="alert" className="mt-3 rounded bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-500/10 dark:text-red-400">
          {resolve.error.message}
        </p>
      )}
      <table className="mt-4 w-full text-left text-sm">
        <thead className="text-xs uppercase text-slate-400">
          <tr><th>Payment</th><th>Score</th><th>Fired rules</th><th>When</th><th /></tr>
        </thead>
        <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
          {data.map((r) => (
            <tr key={r.payment_id}>
              <td className="py-2 font-mono text-xs">{r.payment_id}</td>
              <td className="font-semibold text-amber-600">
              {r.score < 0 ? "Unavailable" : r.score}
              </td>
              <td className="py-2 text-xs">
                <details>
                  <summary className="cursor-pointer">
                    {r.fired_rules || "No rule names recorded"}
                  </summary>
                  <pre className="mt-2 max-w-sm overflow-auto whitespace-pre-wrap">
                    {r.signals}
                  </pre>
                </details>
              </td>
              <td className="text-xs">{new Date(r.created_at).toLocaleString()}</td>
              <td className="space-x-2 text-right">
                <button onClick={() => requestResolution(r.payment_id, "approve")}
                  disabled={!canResolve || resolve.isPending}
                  className="rounded bg-emerald-600 px-2.5 py-1 text-xs text-white disabled:opacity-50">Approve</button>
                <button onClick={() => requestResolution(r.payment_id, "reject")}
                  disabled={!canResolve || resolve.isPending}
                  className="rounded bg-red-600 px-2.5 py-1 text-xs text-white disabled:opacity-50">Reject</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}