import { useState } from "react";
import type { Product } from "../products";
import { useCart } from "../cart/cart";
import { formatMoney } from "../lib/money";
import ProductIcon from "./ProductIcon";

export default function ProductCard({ product }: { product: Product }) {
  const { addItem } = useCart();
  const [added, setAdded] = useState(false);

  function handleAdd() {
    addItem(product.id);
    setAdded(true);
    setTimeout(() => setAdded(false), 1200);
  }

  return (
    <div className="group relative flex flex-col overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm transition-all duration-200 hover:-translate-y-1 hover:shadow-lg dark:border-slate-800 dark:bg-slate-900">
      {product.badge && (
        <span className="absolute right-3 top-3 z-[1] rounded-full bg-white/90 px-2.5 py-0.5 text-[11px] font-semibold text-slate-900 shadow-sm backdrop-blur dark:bg-slate-800/90 dark:text-white">
          {product.badge}
        </span>
      )}
      <div
        className={`flex h-36 items-center justify-center bg-gradient-to-br ${product.accent} text-white/90 transition-transform duration-300 group-hover:scale-[1.02]`}
      >
        <ProductIcon icon={product.icon} />
      </div>
      <div className="flex flex-1 flex-col p-5">
        <h2 className="font-semibold leading-snug text-slate-900 dark:text-white">
          {product.name}
        </h2>
        <p className="mt-1.5 flex-1 text-[13px] leading-relaxed text-slate-500 dark:text-slate-400">
          {product.tagline}
        </p>
        <div className="mt-4 flex items-center justify-between">
          <span className="text-lg font-bold text-slate-900 dark:text-white">
            {product.amount === 0
              ? "Free"
              : formatMoney(product.amount, product.currency)}
          </span>
          <button
            onClick={handleAdd}
            className={`rounded-lg px-4 py-2 text-sm font-medium transition-all duration-200 ${
              added
                ? "bg-emerald-600 text-white"
                : "bg-slate-900 text-white hover:bg-slate-700 dark:bg-white dark:text-slate-900 dark:hover:bg-slate-200"
            }`}
          >
            {added ? "Added" : "Add to cart"}
          </button>
        </div>
      </div>
    </div>
  );
}
