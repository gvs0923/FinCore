import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { http, HttpResponse } from "msw";
import { server } from "../test/mocks/server";
import { CartProvider } from "../cart/CartProvider";
import CartPage from "./CartPage";

function CheckoutSentinel() {
  return <p>checkout reached</p>;
}

function renderCart() {
  return render(
    <MemoryRouter initialEntries={["/cart"]}>
      <CartProvider>
        <Routes>
          <Route path="/cart" element={<CartPage />} />
          <Route path="/checkout/:sessionId" element={<CheckoutSentinel />} />
        </Routes>
      </CartProvider>
    </MemoryRouter>,
  );
}

function seedCart(items: { productId: string; qty: number }[]) {
  localStorage.setItem("fincore-cart", JSON.stringify(items));
}

describe("CartPage", () => {
  it("shows an empty state with a link back to the store", () => {
    localStorage.removeItem("fincore-cart");
    renderCart();

    expect(screen.getByText("Your cart is empty")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Browse products" }),
    ).toBeInTheDocument();
  });

  it("lists items, totals them, and navigates to checkout", async () => {
    seedCart([
      { productId: "wallet-topup-25", qty: 1 },  // $25.00
      { productId: "instant-transfer", qty: 2 },  // 2 x $4.99 = $9.98
    ]);
    const user = userEvent.setup();
    renderCart();

    expect(screen.getByText("Wallet Top-up $25")).toBeInTheDocument();
    expect(screen.getByText("Instant Transfer")).toBeInTheDocument();
    expect(screen.getByText("$34.98")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Continue to checkout" }));
    expect(await screen.findByText("checkout reached")).toBeInTheDocument();
  });

  it("shows an error message when session creation fails", async () => {
    server.use(
      http.post("/v1/checkout/sessions", () =>
        HttpResponse.json({ detail: "Service unavailable" }, { status: 503 }),
      ),
    );
    seedCart([{ productId: "wallet-topup-25", qty: 1 }]);
    const user = userEvent.setup();
    renderCart();

    await user.click(screen.getByRole("button", { name: "Continue to checkout" }));

    expect(await screen.findByText("Service unavailable")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continue to checkout" })).toBeEnabled();
  });
});
