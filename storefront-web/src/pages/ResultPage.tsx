import { useEffect, useState, type ReactNode } from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { getSession, type SessionResponse } from "../api/client";
import { formatMoney } from "../lib/money";

function Shell({ children }: { children: ReactNode }) {
  return (
    <main
      className="mx-auto flex max-w-md flex-col items-center px-6 py-20 text-center"
      aria-live="polite"
    >
      {children}
    </main>
  );
}

function StatusIcon({ variant }: { variant: "success" | "failure" | "pending" }) {
  if (variant === "pending") {
    return (
      <div className="h-16 w-16 animate-spin rounded-full border-4 border-slate-200 border-t-indigo-600 dark:border-slate-700 dark:border-t-indigo-400" />
    );
  }
  const isSuccess = variant === "success";
  return (
    <svg
      viewBox="0 0 52 52"
      className={`result-icon h-16 w-16 ${isSuccess ? "text-emerald-500" : "text-red-500"}`}
    >
      <circle cx="26" cy="26" r="24" fill="none" stroke="currentColor" strokeWidth="2.5" className="stroke" />
      {isSuccess ? (
        <path fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round" d="M14 27l8 8 16-17" className="stroke" />
      ) : (
        <path fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" d="M17 17l18 18M35 17L17 35" className="stroke" />
      )}
    </svg>
  );
}

export default function ResultPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const [sseResult, setSseResult] = useState<SessionResponse | null>(null);
  const [sseActive, setSseActive] = useState(true);
  const [timedOut, setTimedOut] = useState(false);

  // Attempt SSE first
  useEffect(() => {
    if (!sessionId) return;
    const eventSource = new EventSource(`/v1/checkout/sessions/${sessionId}/events`);

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
      setSseActive(false);
    };

    return () => eventSource.close();
  }, [sessionId]);

  // Polling fallback
  const { data: polledData, error } = useQuery({
    queryKey: ["session", sessionId],
    queryFn: () => getSession(sessionId!),
    enabled:
      !!sessionId &&
      !sseActive &&
      !timedOut &&
      (!sseResult || sseResult.status === "REVIEW"),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === "SUCCEEDED" || status === "FAILED" ? false : 1500;
    },
  });

  const data =
    sseResult?.status === "REVIEW"
      ? polledData ?? sseResult
      : sseResult ?? polledData;

  if (error) {
    return (
      <Shell>
        <StatusIcon variant="failure" />
        <p className="mt-6 text-slate-500">Could not load session.</p>
      </Shell>
    );
  }

  if (timedOut) {
    return (
      <Shell>
        <StatusIcon variant="pending" />
        <h1 className="mt-6 text-xl font-bold text-amber-600 dark:text-amber-400">
          Still processing
        </h1>
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
          Your payment is taking longer than expected. We&apos;ll notify you when it completes.
        </p>
        <Link
          to="/"
          className="mt-8 rounded-lg border border-slate-200 px-5 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
        >
          Back to dashboard
        </Link>
      </Shell>
    );
  }

  if (!data || data.status === "PROCESSING" || data.status === "OPEN") {
    return (
      <Shell>
        <StatusIcon variant="pending" />
        <p className="mt-6 text-slate-500 dark:text-slate-400">
          Processing your payment&hellip;
        </p>
      </Shell>
    );
  }

  if (data.status === "SUCCEEDED") {
    return (
      <Shell>
        <StatusIcon variant="success" />
        <h1 className="mt-6 text-2xl font-bold text-slate-900 dark:text-white">
          Payment succeeded
        </h1>
        <div className="mt-6 w-full space-y-3 rounded-2xl border border-slate-200/80 bg-white p-5 text-left text-sm dark:border-slate-800 dark:bg-slate-900">
          {data.description && (
            <div className="flex justify-between gap-4">
              <span className="text-slate-500 dark:text-slate-400">Order</span>
              <span className="text-right text-slate-900 dark:text-white">{data.description}</span>
            </div>
          )}
          {data.amount != null && data.currency && (
            <div className="flex justify-between gap-4">
              <span className="text-slate-500 dark:text-slate-400">Amount</span>
              <span className="font-semibold text-slate-900 dark:text-white">
                {formatMoney(data.amount, data.currency)}
              </span>
            </div>
          )}
          <div className="flex justify-between gap-4 border-t border-slate-100 pt-3 dark:border-slate-800">
            <span className="text-slate-500 dark:text-slate-400">Payment ID</span>
            <span className="font-mono text-xs text-slate-600 dark:text-slate-300">
              Payment ID: {data.paymentId}
            </span>
          </div>
        </div>
        <Link
          to="/"
          className="mt-8 rounded-lg bg-indigo-600 px-6 py-2.5 text-sm font-medium text-white transition hover:bg-indigo-500"
        >
          Back to dashboard
        </Link>
      </Shell>
    );
  }

    if (data.status === "REVIEW") {
    return (
      <Shell>
        <StatusIcon variant="pending" />
        <h1 className="mt-6 text-2xl font-bold text-amber-600 dark:text-amber-400">
          Order pending review
        </h1>
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
          Your payment is being reviewed by our team. We will notify you
          once it is approved .
        </p>
        <Link
          to="/"
          className="mt-8 rounded-lg border border-slate-200 px-5 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
        >
          Back to store
        </Link>
      </Shell>
    );
  }

  return (
    <Shell>
      <StatusIcon variant="failure" />
      <h1 className="mt-6 text-2xl font-bold text-slate-900 dark:text-white">
        Payment failed
      </h1>
      <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
        {data.failureReason ?? "Unknown reason"}
      </p>
      <div className="mt-8 flex gap-3">
        <Link
          to={`/checkout/${sessionId}`}
          className="rounded-lg bg-indigo-600 px-5 py-2 text-sm font-medium text-white transition hover:bg-indigo-500"
        >
          Try again
        </Link>
        <Link
          to="/"
          className="rounded-lg border border-slate-200 px-5 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
        >
          Back to dashboard
        </Link>
      </div>
    </Shell>
  );
}
