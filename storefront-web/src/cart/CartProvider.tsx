import { useEffect, useMemo, useState, type ReactNode } from "react";
import {
  CartContext,
  CART_STORAGE_KEY,
  loadCart,
  type CartContextValue,
  type CartItem,
} from "./cart";
import { getProduct } from "../products";

export function CartProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<CartItem[]>(loadCart);

  useEffect(() => {
    localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(items));
  }, [items]);

  const value = useMemo<CartContextValue>(() => {
    const detailed = items
      .map((i) => ({ ...i, product: getProduct(i.productId)! }))
      .filter((i) => i.product);
    return {
      items,
      count: items.reduce((n, i) => n + i.qty, 0),
      totalMinor: detailed.reduce((n, i) => n + i.product.amount * i.qty, 0),
      currency: detailed[0]?.product.currency ?? "USD",
      addItem: (productId) =>
        setItems((prev) => {
          const existing = prev.find((i) => i.productId === productId);
          if (existing) {
            return prev.map((i) =>
              i.productId === productId ? { ...i, qty: i.qty + 1 } : i,
            );
          }
          return [...prev, { productId, qty: 1 }];
        }),
      removeItem: (productId) =>
        setItems((prev) => prev.filter((i) => i.productId !== productId)),
      setQty: (productId, qty) =>
        setItems((prev) =>
          qty <= 0
            ? prev.filter((i) => i.productId !== productId)
            : prev.map((i) => (i.productId === productId ? { ...i, qty } : i)),
        ),
      clear: () => setItems([]),
    };
  }, [items]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}
