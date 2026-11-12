import { StrictMode, lazy, Suspense } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import "./index.css";
import { CartProvider } from "./cart/CartProvider";
import { ThemeProvider } from "./theme/ThemeProvider";
import Layout from "./components/Layout";
import CataloguePage from "./pages/CataloguePage";
import CartPage from "./pages/CartPage";
import CheckoutPage from "./pages/CheckoutPage";
import ResultPage from "./pages/ResultPage";

const queryClient = new QueryClient();

const DevHarnessPage = import.meta.env.DEV
  ? lazy(() => import("./pages/DevHarnessPage"))
  : null;

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <CartProvider>
          <BrowserRouter>
            <Routes>
              <Route element={<Layout />}>
                <Route path="/" element={<CataloguePage />} />
                <Route path="/cart" element={<CartPage />} />
                <Route path="/checkout/:sessionId" element={<CheckoutPage />} />
                <Route path="/result/:sessionId" element={<ResultPage />} />
                {DevHarnessPage && (
                  <Route
                    path="/dev/risk"
                    element={
                      <Suspense fallback={<p role="status">Loading harness…</p>}>
                        <DevHarnessPage />
                      </Suspense>
                    }
                  />
                )}
              </Route>
            </Routes>
          </BrowserRouter>
        </CartProvider>
      </ThemeProvider>
    </QueryClientProvider>
  </StrictMode>,
);
