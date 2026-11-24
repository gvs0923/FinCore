import { useEffect, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ADMIN_SESSION_EXPIRED, AdminAuthError, AdminForbiddenError, fetchMe, login, logout, takeReturnPath,
         type AdminUser } from "../api/adminClient";
import { AdminUserContext } from "../auth/adminUser";

type Gate =
  | { kind: "loading" }
  | { kind: "ready"; user: AdminUser }
  | { kind: "forbidden" }
  | { kind: "expired" }
  | { kind: "error"; message: string };

export default function AdminGate({ children }: { children: ReactNode }) {
  const [gate, setGate] = useState<Gate>({ kind: "loading" });
  const queryClient = useQueryClient();

  useEffect(() => {
    let cancelled = false;
    const expired = () => {
      queryClient.clear();
      setGate({ kind: "expired" });
    };
    window.addEventListener(ADMIN_SESSION_EXPIRED, expired);
    fetchMe().then(
      (user) => {
        if (cancelled) return;
        const back = takeReturnPath();
        if (back && back !== window.location.pathname) window.history.replaceState(null, "", back);
        setGate({ kind: "ready", user });
      },
      (error: unknown) => {
        if (cancelled) return;
        if (error instanceof AdminAuthError) return login();
        setGate(error instanceof AdminForbiddenError
          ? { kind: "forbidden" }
          : { kind: "error", message: error instanceof Error ? error.message : "Unknown error" });
      },
    );
    return () => {
      cancelled = true;
      window.removeEventListener(ADMIN_SESSION_EXPIRED, expired);
    };
  }, [queryClient]);

  if (gate.kind === "ready") {
    return <AdminUserContext.Provider value={gate.user}>{children}</AdminUserContext.Provider>;
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-slate-950">
      <div role="status" aria-live="polite"
           className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-8 text-center shadow-lg dark:border-slate-800 dark:bg-slate-900 dark:text-white">
        {gate.kind === "loading" && "Checking your session…"}
        {gate.kind === "forbidden" && (
          <>
            <p className="mb-4">Your account has no FinCore admin role.</p>
            <button onClick={() => void logout().catch((error: Error) => setGate({ kind: "error", message: error.message }))}
                    className="rounded-lg bg-indigo-600 px-4 py-2 text-sm text-white">
              Sign out
            </button>
          </>
        )}
        {gate.kind === "expired" && <>
          <p>Your session expired. Sign in again to continue.</p>
          <button onClick={login}>Sign in</button>
        </>}
        {gate.kind === "error" && `Could not reach the admin API: ${gate.message}`}
      </div>
    </div>
  );
}
