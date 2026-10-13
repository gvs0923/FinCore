export type CardBrand = "VISA" | "MASTERCARD" | "AMEX" | "DISCOVER" | "UNKNOWN";

export function detectCardBrand(digits: string): CardBrand {
  if (/^4/.test(digits)) return "VISA";
  if (/^(5[1-5]|2[2-7])/.test(digits)) return "MASTERCARD";
  if (/^3[47]/.test(digits)) return "AMEX";
  if (/^6/.test(digits)) return "DISCOVER";
  return "UNKNOWN";
}

export const BRAND_LABEL: Record<CardBrand, string> = {
  VISA: "Visa",
  MASTERCARD: "Mastercard",
  AMEX: "Amex",
  DISCOVER: "Discover",
  UNKNOWN: "Card",
};

/** Groups digits into blocks of four for display: "424242…" → "4242 4242 …". */
export function formatCardNumber(value: string): string {
  return value
    .replace(/\D/g, "")
    .slice(0, 19)
    .replace(/(\d{4})(?=\d)/g, "$1 ");
}

/** Keeps digits and inserts the slash: "1228" → "12/28". */
export function formatExpiry(value: string): string {
  const digits = value.replace(/\D/g, "").slice(0, 4);
  if (digits.length <= 2) return digits;
  return `${digits.slice(0, 2)}/${digits.slice(2)}`;
}
