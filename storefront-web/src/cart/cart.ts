import { createContext, useContext } from "react";
import { getProduct } from "../products";

export interface CartItem {
  productId: string;
  qty: number;
}

export interface CartContextValue {
  items: CartItem[];
  count: number;
  totalMinor: number;
  currency: string;
  addItem: (productId: string) => void;
  removeItem: (productId: string) => void;
  setQty: (productId: string, qty: number) => void;
  clear: () => void;
}

export const CartContext = createContext<CartContextValue | null>(null);

export const CART_STORAGE_KEY = "fincore-cart";

export function loadCart(): CartItem[] {
  try {
    const raw = localStorage.getItem(CART_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as CartItem[];
    return parsed.filter(
      (i) => typeof i.productId === "string" && i.qty > 0 && getProduct(i.productId),
    );
  } catch {
    return [];
  }
}

export function useCart(): CartContextValue {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error("useCart must be used inside <CartProvider>");
  return ctx;
}
