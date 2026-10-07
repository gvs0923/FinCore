import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { getSession, type SessionResponse } from "../api/client";

export default function ResultPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const [sseResult, setSseResult] = useState<SessionResponse | null>(null);
  const [sseActive, setSseActive] = useState(true);
  const [timedOut, setTimedOut] = useState(false);

  // Attempt SSE first
  useEffect(() => {
    if (!sessionId) return;
    const eventSource = new EventSource(
      `/v1/checkout/sessions/${sessionId}/events`
    );

    eventSource.addEventListener("status", (e) => {
      const data: SessionResponse = JSON.parse(e.data);
      setSseResult(data);
      eventSource.close();
      setSseActive(false);
    });

    eventSource.addEventListener("timeout", () => {
      setTimedOut(true);
      eventSource.close();
      setSseActive(false);
    });

    eventSource.onerror = () => {
      eventSource.close();
      setSseActive(false);   // fall back to polling
    };

    return () => eventSource.close();
  }, [sessionId]);

  // Polling fallback — only runs when SSE is not active and no result yet
  const { data: polledData, error } = useQuery({
    queryKey: ["session", sessionId],
    queryFn: () => getSession(sessionId!),
    enabled: !!sessionId && !sseActive && !sseResult && !timedOut,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === "SUCCEEDED" || status === "FAILED" ? false : 1500;
    },
  });

  const data = sseResult ?? polledData;

  if (error) {
    return (
      <main className="p-8" aria-live="polite">
        <p>Could not load session.</p>
      </main>
    );
  }

  if (timedOut) {
    return (
      <main className="p-8" aria-live="polite">
        <h1 className="text-xl font-bold text-yellow-700">
          Still processing
        </h1>
        <p>
          Your payment is taking longer than expected. We'll email you
          when it completes.
        </p>
      </main>
    );
  }

  if (!data || data.status === "PROCESSING" || data.status === "OPEN") {
    return (
      <main className="p-8" aria-live="polite">
        <p>Processing your payment…</p>
      </main>
    );
  }

  if (data.status === "SUCCEEDED") {
    return (
      <main className="p-8" aria-live="polite">
        <h1 className="text-xl font-bold text-green-700">
          Payment succeeded
        </h1>
        <p>Payment ID: {data.paymentId}</p>
      </main>
    );
  }

  return (
    <main className="p-8" aria-live="polite">
      <h1 className="text-xl font-bold text-red-700">Payment failed</h1>
      <p>{data.failureReason ?? "Unknown reason"}</p>
    </main>
  );
}