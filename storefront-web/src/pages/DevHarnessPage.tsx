import { useState } from "react";
import { createSession, paySession } from "../api/client";
import { getDeviceId } from "../lib/device";

const TEST_CARD = "4242424242424242";

export default function DevHarnessPage() {
  const [log, setLog] = useState<string[]>([]);

  async function fire(n: number) {
    for (let i = 0; i < n; i++) {
      try {
        const s = await createSession(500, "USD", `harness-${i}`);
        if (!s.sessionId) throw new Error("no sessionId in response");
        const r = await paySession(s.sessionId, TEST_CARD, "VISA", "4242", {
          deviceId: getDeviceId(),
          timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        });
        setLog((l) => [...l, `#${i + 1}: ${r.status}`]);
      } catch (e) {
        setLog((l) => [...l, `#${i + 1}: ${e instanceof Error ? e.message : "err"}`]);
      }
    }
  }

  if (!import.meta.env.DEV) return <p className="p-8">Dev only.</p>;

  return (
    <main className="mx-auto max-w-xl px-6 py-10">
      <h1 className="text-xl font-bold">Velocity harness</h1>
      <p className="mt-1 text-sm text-slate-500">
        Fires rapid checkouts on the same card fingerprint — watch the
        risk engine escalate ALLOW → REVIEW → BLOCK.
      </p>
      <div className="mt-4 flex gap-2">
        {[1, 5, 10].map((n) => (
          <button key={n} onClick={() => fire(n)}
            className="rounded bg-indigo-600 px-3 py-1.5 text-sm text-white">
            Fire {n}
          </button>
        ))}
        <button onClick={() => setLog([])} className="px-3 py-1.5 text-sm">Clear</button>
      </div>
      <pre className="mt-4 rounded-lg bg-slate-100 p-3 text-xs dark:bg-slate-800">
        {log.join("\n")}
      </pre>
    </main>
  );
}