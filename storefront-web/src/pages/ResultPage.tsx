import { useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { getSession } from "../api/client";

export default function ResultPage() {
  const { sessionId } = useParams<{ sessionId: string }>();

  const { data, error } = useQuery({
    queryKey: ["session", sessionId],
    queryFn: () => getSession(sessionId!),
    enabled: !!sessionId,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === "SUCCEEDED" || status === "FAILED" ? false : 1500;
    },
  });

  if (error) {
    return <main className="p-8 text-red-600">Could not load session.</main>;
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
        <h1 className="text-xl font-bold text-green-700">Payment succeeded</h1>
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