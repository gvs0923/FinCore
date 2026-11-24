import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import "../index.css";
import { ThemeProvider } from "../theme/ThemeProvider";
import AdminGate from "./components/AdminGate";
import AdminLayout from "./components/AdminLayout";
import DashboardPage from "./pages/DashboardPage";
import ReviewQueuePage from "./pages/ReviewQueuePage";
import StubPage from "./pages/StubPage";
import LedgerPage from "./pages/LedgerPage";
import SettlementPage from "./pages/SettlementPage";
import SettlementDetailPage from "./pages/SettlementDetailPage";
import ReconciliationPage from "./pages/ReconciliationPage";
import OrdersPage from "./pages/OrdersPage";
import OrderDetailPage from "./pages/OrderDetailPage";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchInterval: 30_000,
      staleTime: 15_000,
    },
  },
});

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AdminGate>
          <BrowserRouter basename="/admin">
            <Routes>
              <Route element={<AdminLayout />}>
                <Route index element={<DashboardPage />} />
                <Route path="traffic" element={<StubPage title="Traffic" />} />
                <Route path="payments" element={<OrdersPage />} />
                <Route path="payments/:paymentId" element={<OrderDetailPage />} />
                <Route path="ledger" element={<LedgerPage />} />
                <Route path="settlement" element={<SettlementPage />} />
                <Route path="settlement/:batchId" element={<SettlementDetailPage />} />
                <Route path="settlement/reconciliation/:runId" element={<ReconciliationPage />} />
                <Route path="risk" element={<ReviewQueuePage />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </AdminGate>
      </ThemeProvider>
    </QueryClientProvider>
  </StrictMode>,
);