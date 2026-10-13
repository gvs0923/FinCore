export type Category = "processing" | "wallet" | "plans";

export interface Product {
  id: string;
  name: string;
  tagline: string;
  amount: number; // minor units
  currency: string;
  category: Category;
  icon: "card" | "shield" | "wallet" | "bolt" | "code" | "rocket";
  accent: string;
  badge?: string;
}

export const CATEGORY_LABELS: Record<Category, string> = {
  processing: "Payment Processing",
  wallet: "Digital Wallet",
  plans: "Plans & Billing",
};

export const PRODUCTS: Product[] = [
  // ── Payment Processing ──────────────────────────────────
  {
    id: "enterprise-processing",
    name: "Enterprise Processing",
    tagline:
      "Interchange-plus pricing, multi-acquirer routing, and 3-D Secure for every market.",
    amount: 49900,
    currency: "USD",
    category: "processing",
    icon: "card",
    accent: "from-indigo-500 to-violet-700",
    badge: "Popular",
  },
  {
    id: "fraud-shield",
    name: "Fraud Shield",
    tagline:
      "ML-powered risk scoring on every transaction. Velocity checks, device fingerprinting, adaptive 3DS.",
    amount: 19900,
    currency: "USD",
    category: "processing",
    icon: "shield",
    accent: "from-rose-500 to-pink-700",
  },

  // ── Digital Wallet ──────────────────────────────────────
  {
    id: "wallet-topup-25",
    name: "Wallet Top-up $25",
    tagline:
      "Load balance into your FinCore wallet. Funds available instantly for transfers and purchases.",
    amount: 2500,
    currency: "USD",
    category: "wallet",
    icon: "wallet",
    accent: "from-emerald-500 to-teal-700",
  },
  {
    id: "wallet-topup-100",
    name: "Wallet Top-up $100",
    tagline:
      "Bulk balance reload. Same instant availability, better value for high-volume senders.",
    amount: 10000,
    currency: "USD",
    category: "wallet",
    icon: "wallet",
    accent: "from-cyan-500 to-blue-600",
  },
  {
    id: "instant-transfer",
    name: "Instant Transfer",
    tagline:
      "Priority same-day payout to your bank account. Skip the 2-day settlement queue.",
    amount: 499,
    currency: "USD",
    category: "wallet",
    icon: "bolt",
    accent: "from-amber-400 to-orange-600",
    badge: "New",
  },

  // ── Plans & Billing ─────────────────────────────────────
  {
    id: "developer-plan",
    name: "Developer Plan",
    tagline:
      "10K API calls/mo, full sandbox access, webhook retries, and community support.",
    amount: 2900,
    currency: "USD",
    category: "plans",
    icon: "code",
    accent: "from-sky-500 to-blue-700",
  },
  {
    id: "scale-plan",
    name: "Scale Plan",
    tagline:
      "Unlimited API calls, real-time analytics dashboard, priority support, and dedicated account manager.",
    amount: 14900,
    currency: "USD",
    category: "plans",
    icon: "rocket",
    accent: "from-violet-500 to-purple-700",
  },
];

export function getProduct(id: string): Product | undefined {
  return PRODUCTS.find((p) => p.id === id);
}
