export default function Footer() {
  return (
    <footer className="mt-auto border-t border-slate-200/60 bg-white dark:border-slate-800 dark:bg-slate-950">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-6 py-6 text-xs text-slate-600 dark:text-slate-400">
        <div className="flex items-center gap-4">
          <span className="font-medium text-slate-500 dark:text-slate-400">
            FinCore
          </span>
          <span className="hidden sm:inline">
            Open-source payment infrastructure
          </span>
        </div>
        <div className="flex items-center gap-4">
          <span>Test environment &middot; No real money</span>
          <a
            href="https://github.com"
            target="_blank"
            rel="noopener noreferrer"
            className="transition hover:text-slate-600 dark:hover:text-slate-300"
          >
            GitHub
          </a>
        </div>
      </div>
    </footer>
  );
}
