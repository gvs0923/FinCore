import { useState } from "react";
import {
  PRODUCTS,
  CATEGORY_LABELS,
  type Category,
} from "../products";
import ProductCard from "../components/ProductCard";

const CATEGORIES: (Category | "all")[] = [
  "all",
  "processing",
  "wallet",
  "plans",
];

export default function CataloguePage() {
  const [filter, setFilter] = useState<Category | "all">("all");

  const visible =
    filter === "all"
      ? PRODUCTS
      : PRODUCTS.filter((p) => p.category === filter);

  return (
    <main>
      {/* Hero */}
      <section className="relative overflow-hidden border-b border-slate-200/60 bg-white dark:border-slate-800 dark:bg-slate-900/60">
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_80%_50%_at_50%_-20%,rgba(99,102,241,0.12),transparent)] dark:bg-[radial-gradient(ellipse_80%_50%_at_50%_-20%,rgba(99,102,241,0.08),transparent)]" />
        <div className="relative mx-auto max-w-6xl px-6 py-20 sm:py-24">
          <div className="mx-auto max-w-2xl text-center">
            <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700 dark:border-emerald-500/20 dark:bg-emerald-500/10 dark:text-emerald-400">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
              All systems operational
            </span>
            <h1 className="mt-5 text-4xl font-bold tracking-tight text-slate-900 sm:text-5xl dark:text-white">
              The payment platform
              <br />
              <span className="text-indigo-600 dark:text-indigo-400">
                built for builders
              </span>
            </h1>
            <p className="mt-5 text-lg leading-relaxed text-slate-500 dark:text-slate-400">
              Process payments, manage wallets, and scale with confidence.
              Every transaction runs through a double-entry ledger with
              idempotent capture. Test cards only.
            </p>
          </div>
        </div>
      </section>

      {/* Trust bar */}
      <div className="border-b border-slate-200/60 bg-slate-50/80 dark:border-slate-800 dark:bg-slate-900/40">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-center gap-x-10 gap-y-3 px-6 py-4 text-xs font-medium text-slate-500 dark:text-slate-400">
          <span className="flex items-center gap-1.5">
            <svg className="h-4 w-4 text-emerald-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/><path d="m9 12 2 2 4-4"/></svg>
            Idempotent capture
          </span>
          <span className="flex items-center gap-1.5">
            <svg className="h-4 w-4 text-indigo-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/></svg>
            Double-entry ledger
          </span>
          <span className="flex items-center gap-1.5">
            <svg className="h-4 w-4 text-amber-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M13 2 4.1 12.9a.5.5 0 0 0 .4.8H12l-1 8.3 8.9-11a.5.5 0 0 0-.4-.8H12l1-8.2Z"/></svg>
            Real-time settlement
          </span>
          <span className="flex items-center gap-1.5">
            <svg className="h-4 w-4 text-slate-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>
            Developer-first API
          </span>
        </div>
      </div>

      {/* Catalogue */}
      <section className="mx-auto max-w-6xl px-6 py-10">
        {/* Category tabs */}
        <div className="mb-8 flex flex-wrap gap-2">
          {CATEGORIES.map((cat) => {
            const active = filter === cat;
            return (
              <button
                key={cat}
                onClick={() => setFilter(cat)}
                className={`rounded-full px-4 py-1.5 text-sm font-medium transition ${
                  active
                    ? "bg-slate-900 text-white dark:bg-white dark:text-slate-900"
                    : "bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700"
                }`}
              >
                {cat === "all" ? "All" : CATEGORY_LABELS[cat]}
              </button>
            );
          })}
        </div>

        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {visible.map((p) => (
            <ProductCard key={p.id} product={p} />
          ))}
        </div>
      </section>
    </main>
  );
}
