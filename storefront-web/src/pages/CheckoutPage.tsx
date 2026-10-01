import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { checkoutFormSchema, type CheckoutFormValues } from "../checkoutSchema";
import { paySession } from "../api/client";

export default function CheckoutPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const [submitError, setSubmitError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<CheckoutFormValues>({
    resolver: zodResolver(checkoutFormSchema),
  });

  async function onSubmit(values: CheckoutFormValues) {
    if (!sessionId) return;
    setSubmitError(null);
    try {
      const cardLast4 = values.cardNumber.slice(-4);
      await paySession(sessionId, values.cardNumber, "VISA", cardLast4);
      navigate(`/result/${sessionId}`);
    } catch (e) {
      setSubmitError(e instanceof Error ? e.message : "Payment failed");
    }
  }

  return (
    <main className="mx-auto max-w-md p-8">
      <h1 className="text-2xl font-bold mb-4">Checkout</h1>
      <p className="text-sm text-gray-500 mb-4">Test cards only — e.g. 4242 4242 4242 4242</p>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label htmlFor="cardNumber" className="block text-sm font-medium">Card number</label>
          <input id="cardNumber" {...register("cardNumber")} className="border rounded w-full p-2" />
          {errors.cardNumber && <p className="text-red-600 text-sm">{errors.cardNumber.message}</p>}
        </div>
        <div>
          <label htmlFor="expiry" className="block text-sm font-medium">Expiry (MM/YY)</label>
          <input id="expiry" {...register("expiry")} className="border rounded w-full p-2" />
          {errors.expiry && <p className="text-red-600 text-sm">{errors.expiry.message}</p>}
        </div>
        <div>
          <label htmlFor="cvv" className="block text-sm font-medium">CVV</label>
          <input id="cvv" {...register("cvv")} className="border rounded w-full p-2" />
          {errors.cvv && <p className="text-red-600 text-sm">{errors.cvv.message}</p>}
        </div>
        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full bg-blue-600 text-white rounded py-2 disabled:opacity-50"
        >
          {isSubmitting ? "Paying…" : "Pay"}
        </button>
        {submitError && <p className="text-red-600">{submitError}</p>}
      </form>
    </main>
  );
}