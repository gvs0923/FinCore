import { useState, type ReactNode } from "react";
import { getAdminToken, setAdminToken } from "../api/adminClient";

export default function AdminGate({ children }: { children: ReactNode }) {
  const [token, setToken] = useState(getAdminToken);
  const [input, setInput] = useState("");

  if (token) return <>{children}</>;

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-slate-950">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setAdminToken(input);
          setToken(input);
        }}
        className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-8 shadow-lg dark:border-slate-800 dark:bg-slate-900"
      >
        <h1 className="mb-6 text-center text-lg font-semibold text-slate-900 dark:text-white">
          Admin Console
        </h1>
        <label className="mb-1.5 block text-sm font-medium text-slate-600 dark:text-slate-400">
          Admin Token
        </label>
        <input
          type="password"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          className="mb-4 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 dark:border-slate-700 dark:bg-slate-800 dark:text-white"
          placeholder="Enter admin token"
          autoFocus
        />
        <button
          type="submit"
          disabled={!input}
          className="w-full rounded-lg bg-indigo-600 py-2 text-sm font-medium text-white transition hover:bg-indigo-700 disabled:opacity-50"
        >
          Enter
        </button>
      </form>
    </div>
  );
}