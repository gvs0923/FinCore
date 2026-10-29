import { Outlet, NavLink } from "react-router-dom";
import { useTheme } from "../../theme/theme";

const NAV_ITEMS = [
  { to: "/", label: "Overview", end: true },
  { to: "/traffic", label: "Traffic" },
  { to: "/payments", label: "Payments" },
  { to: "/ledger", label: "Ledger" },
];

export default function AdminLayout() {
  const { theme, toggle } = useTheme();

  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-700 dark:bg-slate-950 dark:text-slate-300">
      <header className="sticky top-0 z-10 border-b border-slate-200/60 bg-white/80 backdrop-blur-lg dark:border-slate-800 dark:bg-slate-950/80">
        <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-6">
          <div className="flex items-center gap-3">
            <svg viewBox="0 0 28 28" className="h-7 w-7" aria-hidden>
              <rect width="28" height="28" rx="7" fill="url(#fc-g)" />
              <path d="M8 10h12M8 14h8M8 18h10" stroke="#fff" strokeWidth="2" strokeLinecap="round" />
              <defs>
                <linearGradient id="fc-g" x1="0" y1="0" x2="28" y2="28">
                  <stop stopColor="#6366f1" />
                  <stop offset="1" stopColor="#8b5cf6" />
                </linearGradient>
              </defs>
            </svg>
            <span className="text-[15px] font-semibold tracking-tight text-slate-900 dark:text-white">
              FinCore
            </span>
            <span className="rounded bg-amber-100 px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wider text-amber-700 dark:bg-amber-500/10 dark:text-amber-400">
              Admin
            </span>
          </div>

          <button
            onClick={toggle}
            aria-label={theme === "dark" ? "Switch to light mode" : "Switch to dark mode"}
            className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-slate-800 dark:hover:text-white"
          >
            {theme === "dark" ? (
              <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                <circle cx="12" cy="12" r="4" />
                <path d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
              </svg>
            ) : (
              <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
              </svg>
            )}
          </button>
        </div>
      </header>

      <nav className="border-b border-slate-200/60 bg-white/60 dark:border-slate-800 dark:bg-slate-950/60">
        <div className="mx-auto flex max-w-7xl gap-1 px-6">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `border-b-2 px-3 py-2.5 text-sm font-medium transition ${
                  isActive
                    ? "border-indigo-500 text-indigo-600 dark:text-indigo-400"
                    : "border-transparent text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200"
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </div>
      </nav>

      <main className="flex-1">
        <Outlet />
      </main>

      <footer className="border-t border-slate-200/60 py-4 text-center text-xs text-slate-400 dark:border-slate-800 dark:text-slate-500">
        FinCore Admin Console
      </footer>
    </div>
  );
}