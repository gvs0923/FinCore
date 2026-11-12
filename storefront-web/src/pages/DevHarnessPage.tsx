import { useState } from "react";
import { createSession, paySession } from "../api/client";
import { getDeviceId } from "../lib/device";

const TEST_CARD = "4242424242424242";

export default function DevHarnessPage() {
  const [log, setLog] = useState<string[]>([]);
  const [running, setRunning] = useState(false);

async function fire(n: number) {
  if (running || n < 1 || n > 10) return;

  setRunning(true);

  try {
    for (let i = 0; i < n; i++) {
      try {
        const session = await createSession(500, "USD", `harness-${i}`);

        if (!session.sessionId) {
          throw new Error("No sessionId in response");
        }

        const result = await paySession(
          session.sessionId,
          TEST_CARD,
          "VISA",
          "4242",
          {
            deviceId: getDeviceId(),
            context: {
              timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
              country: "US",
            },
          },
        );

        setLog((previous) => [
          ...previous,
          `#${i + 1}: ${result.status}`,
        ]);
      } catch (error) {
        setLog((previous) => [
          ...previous,
          `#${i + 1}: ${
            error instanceof Error ? error.message : "Request failed"
          }`,
        ]);
      }
    }
  } finally {
    setRunning(false);
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
          <button key={n} onClick={() => fire(n)} disabled={running}
            className="rounded bg-indigo-600 px-3 py-1.5 text-sm text-white disabled:opacity-50">
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