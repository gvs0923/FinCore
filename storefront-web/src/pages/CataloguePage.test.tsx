import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { http, HttpResponse } from "msw";
import { server } from "../test/mocks/server";
import CataloguePage from "./CataloguePage";

/** Renders CataloguePage inside a router so useNavigate works. */
function renderPage() {
  // A sentinel component so we can assert navigation happened.
  function CheckoutSentinel() {
    return <p>checkout reached</p>;
  }

  return render(
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route path="/" element={<CataloguePage />} />
        <Route path="/checkout/:sessionId" element={<CheckoutSentinel />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("CataloguePage", () => {
  it("renders the product name, price, and Buy button", () => {
    renderPage();

    expect(screen.getByText("Premium Widget")).toBeInTheDocument();
    expect(screen.getByText("$50.00")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Buy" })).toBeEnabled();
  });

  it("navigates to /checkout/:sessionId after clicking Buy", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Buy" }));

    // The MSW handler returns sessionId "test-session-123"
    expect(await screen.findByText("checkout reached")).toBeInTheDocument();
  });

  it("shows loading state while session is being created", async () => {
    // Make the handler hang so we can observe the loading state
    server.use(
      http.post("/v1/checkout/sessions", () => {
        return new Promise(() => {}); // never resolves
      }),
    );

    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Buy" }));

    expect(
      await screen.findByRole("button", { name: "Creating session…" }),
    ).toBeDisabled();
  });

  it("shows an error message when the API call fails", async () => {
    server.use(
      http.post("/v1/checkout/sessions", () => {
        return HttpResponse.json(
          { detail: "Service unavailable" },
          { status: 503 },
        );
      }),
    );

    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Buy" }));

    expect(await screen.findByText("Service unavailable")).toBeInTheDocument();
    // Button should be re-enabled after error
    expect(screen.getByRole("button", { name: "Buy" })).toBeEnabled();
  });
});
