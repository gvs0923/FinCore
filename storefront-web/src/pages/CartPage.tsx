import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useCart } from "../cart/cart";
import { getProduct } from "../products";
import { createSession } from "../api/client";
import { formatMoney } from "../lib/money";
import ProductIcon from "../components/ProductIcon";

export default function CartPage() {
  const { items, setQty, removeItem, totalMinor, currency } = useCart();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleCheckout() {
    setLoading(true);
    setError(null);
    try {
      const description = items
        .map((i) => `${i.qty}\u00d7 ${getProduct(i.productId)!.name}`)
        .join(", ");
      const session = await createSession(totalMinor, currency, description);
      navigate(`/checkout/${session.sessionId}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong");
      setLoading(false);
    }
  }

  if (items.length === 0) {
    return (
      <main className="mx-auto max-w-2xl px-6 py-24 text-center">
        <div className="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-2xl bg-slate-100 dark:bg-slate-800">
          <svg viewBox="0 0 24 24" className="h-10 w-10 text-slate-400" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4ZM3 6h18" />
            <path d="M16 10a4 4 0 0 1-8 0" />
          </svg>
        </div>
        <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
          Your cart is empty
        </h1>
        <p className="mt-2 text-slate-500 dark:text-slate-400">
          Browse the catalogue to add services to your cart.
        </p>
        <Link
          to="/"
          className="mt-6 inline-block rounded-lg bg-slate-900 px-6 py-2.5 text-sm font-medium text-white transition hover:bg-slate-700 dark:bg-white dark:text-slate-900 dark:hover:bg-slate-200"
        >
          Browse products
        </Link>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-3xl px-6 py-10">
      <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
        Your cart
      </h1>

      <ul className="mt-6 divide-y divide-slate-100 rounded-2xl border border-slate-200/80 bg-white dark:divide-slate-800 dark:border-slate-800 dark:bg-slate-900">
        {items.map((item) => {
          const product = getProduct(item.productId)!;
          return (
            <li key={item.productId} className="flex items-center gap-4 p-4">
              <div
                className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br ${product.accent} text-white/90`}
              >
                <ProductIcon icon={product.icon} />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-slate-900 dark:text-white">
                  {product.name}
                </p>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  {formatMoney(product.amount, product.currency)} each
                </p>
              </div>
              <div className="flex items-center gap-1">
                <button
                  aria-label={`Decrease quantity of ${product.name}`}
                  onClick={() => setQty(item.productId, item.qty - 1)}
                  className="flex h-7 w-7 items-center justify-center rounded-md border border-slate-200 text-sm text-slate-600 transition hover:bg-slate-100 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
                >
                  -
                </button>
                <span className="w-7 text-center text-sm font-medium text-slate-900 dark:text-white">
                  {item.qty}
                </span>
                <button
                  aria-label={`Increase quantity of ${product.name}`}
                  onClick={() => setQty(item.productId, item.qty + 1)}
                  className="flex h-7 w-7 items-center justify-center rounded-md border border-slate-200 text-sm text-slate-600 transition hover:bg-slate-100 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
                >
                  +
                </button>
              </div>
              <p className="w-20 text-right text-sm font-semibold text-slate-900 dark:text-white">
                {formatMoney(product.amount * item.qty, product.currency)}
              </p>
              <button
                aria-label={`Remove ${product.name}`}
                onClick={() => removeItem(item.productId)}
                className="text-slate-300 transition hover:text-red-500 dark:text-slate-600 dark:hover:text-red-400"
              >
                <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                  <path d="M18 6 6 18M6 6l12 12" />
                </svg>
              </button>
            </li>
          );
        })}
      </ul>

      <div className="mt-6 rounded-2xl border border-slate-200/80 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
        <div className="flex items-baseline justify-between">
          <span className="text-sm text-slate-500 dark:text-slate-400">Total</span>
          <span className="text-xl font-bold text-slate-900 dark:text-white">
            {formatMoney(totalMinor, currency)}
          </span>
        </div>
        <button
          onClick={handleCheckout}
          disabled={loading}
          className="mt-4 w-full rounded-lg bg-slate-900 py-2.5 text-sm font-medium text-white transition hover:bg-slate-700 disabled:opacity-50 dark:bg-white dark:text-slate-900 dark:hover:bg-slate-200"
        >
          {loading ? "Creating session…" : "Continue to checkout"}
        </button>
        {error && (
          <p className="mt-2 text-sm text-red-600 dark:text-red-400">{error}</p>
        )}
      </div>
    </main>
  );
}
