import { useState } from "react";
import { useNavigate, useParams, Link } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import {
  checkoutFormSchema,
  type CheckoutFormInput,
  type CheckoutFormValues,
} from "../checkoutSchema";
import { getSession, paySession } from "../api/client";
import {
  BRAND_LABEL,
  detectCardBrand,
  formatCardNumber,
  formatExpiry,
} from "../lib/card";
import { formatMoney } from "../lib/money";

const inputClass =
  "w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 dark:border-slate-700 dark:bg-slate-800 dark:text-white dark:focus:border-indigo-400";

export default function CheckoutPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const [submitError, setSubmitError] = useState<string | null>(null);

  const { data: session } = useQuery({
    queryKey: ["session", sessionId],
    queryFn: () => getSession(sessionId!),
    enabled: !!sessionId,
  });

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<CheckoutFormInput, unknown, CheckoutFormValues>({
    resolver: zodResolver(checkoutFormSchema),
  });

  const cardNumberField = register("cardNumber");
  const expiryField = register("expiry");
  const cvvField = register("cvv");

  const watchedNumber = String(watch("cardNumber") ?? "");
  const watchedExpiry = watch("expiry") ?? "";
  const digits = watchedNumber.replace(/\D/g, "");
  const brand = detectCardBrand(digits);
  const total =
    session?.amount != null && session.currency
      ? formatMoney(session.amount, session.currency)
      : null;

  async function onSubmit(values: CheckoutFormValues) {
    if (!sessionId) return;
    setSubmitError(null);
    try {
      const cardLast4 = values.cardNumber.slice(-4);
      await paySession(sessionId, values.cardNumber, brand, cardLast4);
      navigate(`/result/${sessionId}`);
    } catch (e) {
      setSubmitError(e instanceof Error ? e.message : "Payment failed");
    }
  }

  return (
    <main className="mx-auto max-w-4xl px-6 py-10">
      <Link
        to="/cart"
        className="inline-flex items-center gap-1 text-sm text-slate-500 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="m15 18-6-6 6-6"/></svg>
        Back to cart
      </Link>
      <h1 className="mt-3 text-2xl font-bold text-slate-900 dark:text-white">
        Checkout
      </h1>

      <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_360px]">
        {/* Payment form */}
        <div className="rounded-2xl border border-slate-200/80 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
          {/* Live card preview */}
          <div className="mb-6 overflow-hidden rounded-xl bg-gradient-to-br from-slate-800 to-slate-950 p-5 text-white shadow-lg dark:from-slate-700 dark:to-slate-900">
            <div className="flex items-center justify-between">
              <span className="h-8 w-11 rounded-md bg-gradient-to-br from-amber-300 to-amber-500" />
              <span className="text-xs font-semibold uppercase tracking-wider text-white/70">
                {BRAND_LABEL[brand]}
              </span>
            </div>
            <p className="mt-5 font-mono text-lg tracking-[0.2em]">
              {formatCardNumber(digits) || "\u2022\u2022\u2022\u2022 \u2022\u2022\u2022\u2022 \u2022\u2022\u2022\u2022 \u2022\u2022\u2022\u2022"}
            </p>
            <div className="mt-4 flex justify-between text-[11px] uppercase tracking-wider text-white/50">
              <span>Cardholder</span>
              <span>{watchedExpiry || "MM/YY"}</span>
            </div>
          </div>

          <div className="mb-5 rounded-lg border border-amber-200 bg-amber-50/80 px-3 py-2.5 text-[13px] text-amber-800 dark:border-amber-500/20 dark:bg-amber-500/10 dark:text-amber-300">
            Test cards only &mdash; use <code className="rounded bg-amber-100 px-1 font-mono text-xs dark:bg-amber-800/30">4242 4242 4242 4242</code> or <code className="rounded bg-amber-100 px-1 font-mono text-xs dark:bg-amber-800/30">4000 0000 0000 0002</code> to decline.
          </div>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div>
              <label htmlFor="cardNumber" className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">
                Card number
              </label>
              <input
                id="cardNumber"
                inputMode="numeric"
                autoComplete="cc-number"
                placeholder="4242 4242 4242 4242"
                {...cardNumberField}
                onChange={(e) => { e.target.value = formatCardNumber(e.target.value); cardNumberField.onChange(e); }}
                className={`font-mono ${inputClass}`}
              />
              {errors.cardNumber && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors.cardNumber.message}</p>}
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label htmlFor="expiry" className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">
                  Expiry (MM/YY)
                </label>
                <input
                  id="expiry"
                  inputMode="numeric"
                  autoComplete="cc-exp"
                  placeholder="12/28"
                  {...expiryField}
                  onChange={(e) => { e.target.value = formatExpiry(e.target.value); expiryField.onChange(e); }}
                  className={`font-mono ${inputClass}`}
                />
                {errors.expiry && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors.expiry.message}</p>}
              </div>
              <div>
                <label htmlFor="cvv" className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">
                  CVV
                </label>
                <input
                  id="cvv"
                  inputMode="numeric"
                  autoComplete="cc-csc"
                  placeholder="123"
                  {...cvvField}
                  onChange={(e) => { e.target.value = e.target.value.replace(/\D/g, "").slice(0, 4); cvvField.onChange(e); }}
                  className={`font-mono ${inputClass}`}
                />
                {errors.cvv && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors.cvv.message}</p>}
              </div>
            </div>
            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full rounded-lg bg-indigo-600 py-2.5 text-sm font-medium text-white transition hover:bg-indigo-500 disabled:opacity-50"
            >
              {isSubmitting ? "Processing\u2026" : total ? `Pay ${total}` : "Pay"}
            </button>
            {submitError && <p className="text-sm text-red-600 dark:text-red-400">{submitError}</p>}
            <p className="text-center text-[11px] text-slate-400 dark:text-slate-500">
              Retrying is safe &mdash; this session reuses the same idempotency key.
            </p>
          </form>
        </div>

        {/* Order summary */}
        <aside className="h-fit rounded-2xl border border-slate-200/80 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
          <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
            Order summary
          </h2>
          {session ? (
            <>
              <p className="mt-3 text-sm text-slate-900 dark:text-white">{session.description}</p>
              <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-4 dark:border-slate-800">
                <span className="text-sm text-slate-500 dark:text-slate-400">Total</span>
                <span className="text-xl font-bold text-slate-900 dark:text-white">{total}</span>
              </div>
            </>
          ) : (
            <div className="mt-4 space-y-2">
              <div className="h-4 w-3/4 animate-pulse rounded bg-slate-100 dark:bg-slate-800" />
              <div className="h-4 w-1/2 animate-pulse rounded bg-slate-100 dark:bg-slate-800" />
            </div>
          )}
        </aside>
      </div>
    </main>
  );
}
