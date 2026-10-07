import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { createSession } from "../api/client";

const PRODUCT = {
  name: "Premium Widget",
  amount: 5000, // minor units — $50.00
  currency: "USD",
};

export default function CataloguePage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleBuy() {
    setLoading(true);
    setError(null);
    try {
      const session = await createSession(PRODUCT.amount, PRODUCT.currency, PRODUCT.name);
      navigate(`/checkout/${session.sessionId}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong");
      setLoading(false);
    }
  }

  return (
    <main className="mx-auto max-w-md p-8">
      <h1 className="text-2xl font-bold mb-4">FinCore Demo Store</h1>
      <div className="border rounded-lg p-4">
        <h2 className="text-lg font-semibold">{PRODUCT.name}</h2>
        <p className="text-gray-600">${(PRODUCT.amount / 100).toFixed(2)}</p>
        <button
          onClick={handleBuy}
          disabled={loading}
          className="mt-4 w-full bg-blue-600 text-white rounded py-2 disabled:opacity-50"
        >
          {loading ? "Creating session…" : "Buy"}
        </button>
        {error && <p className="text-red-600 mt-2">{error}</p>}
      </div>
    </main>
  );
}