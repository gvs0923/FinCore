import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { CartProvider } from "../cart/CartProvider";
import { useCart } from "../cart/cart";
import CataloguePage from "./CataloguePage";

function CartCount() {
  const { count } = useCart();
  return <span data-testid="cart-count">{count}</span>;
}

function renderPage() {
  localStorage.removeItem("fincore-cart");
  return render(
    <MemoryRouter initialEntries={["/"]}>
      <CartProvider>
        <CartCount />
        <CataloguePage />
      </CartProvider>
    </MemoryRouter>,
  );
}

describe("CataloguePage", () => {
  it("renders the product grid with names and prices", () => {
    renderPage();

    expect(screen.getByRole("heading", { level: 2, name: "Enterprise Processing" })).toBeInTheDocument();
    expect(screen.getByText("$499.00")).toBeInTheDocument();
    expect(
      screen.getAllByRole("button", { name: "Add to cart" }).length,
    ).toBeGreaterThan(1);
  });

  it("adds a product to the cart", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(
      screen.getAllByRole("button", { name: "Add to cart" })[0],
    );

    expect(screen.getByTestId("cart-count")).toHaveTextContent("1");
  });
});
