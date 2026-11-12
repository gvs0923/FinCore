import { Link } from "react-router-dom";
import {
  Bar,
  BarChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { RiskSummary } from "../api/adminClient";

export default function RiskSection({
  risk,
  loading,
  error,
}: {
  risk?: RiskSummary;
  loading: boolean;
  error: boolean;
}) {
  return (
    <section className="my-8 rounded-xl border border-slate-200 p-6 dark:border-slate-800">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="font-semibold">Risk · last 24 hours</h2>
        <Link to="/risk" className="text-sm text-indigo-500">
          Review queue
        </Link>
      </div>

      {loading ? (
        <p role="status">Loading risk metrics…</p>
      ) : error || !risk || !Array.isArray(risk.scoreDistribution) ? (
        <p role="alert">Risk metrics are unavailable.</p>
      ) : (
        <>
          <dl className="grid grid-cols-3 gap-4">
            {["ALLOW", "REVIEW", "BLOCK"].map((decision) => (
              <div key={decision}>
                <dt className="text-xs text-slate-500">{decision}</dt>
                <dd className="text-2xl font-semibold">
                  {risk.byDecision.find((row) => row.decision === decision)
                    ?.count ?? 0}
                </dd>
              </div>
            ))}
          </dl>

          <h3 className="mb-3 mt-6 text-sm font-medium">
            Score distribution
          </h3>

          {risk.scoreDistribution.length === 0 ? (
            <p className="text-sm text-slate-500">
              No assessments in this window.
            </p>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={risk.scoreDistribution}>
                <XAxis dataKey="score_bucket" />
                <YAxis allowDecimals={false} />
                <Tooltip />
                <Bar dataKey="count" fill="#6366f1" name="Assessments" />
              </BarChart>
            </ResponsiveContainer>
          )}

          <p className="mt-2 text-xs text-slate-500">
            UNKNOWN means the fallback had no risk-engine score.
          </p>

          <h3 className="mb-2 mt-6 text-sm font-medium">
            Most-fired rules
          </h3>

          <ul className="space-y-2">
            {risk.topRules.map((rule) => (
              <li
                key={rule.rule_fired}
                className="flex justify-between text-sm"
              >
                <span>{rule.rule_fired}</span>
                <span>{rule.count}</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}