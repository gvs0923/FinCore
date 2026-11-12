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
                <Route path="payments" element={<StubPage title="Payments" />} />
                <Route path="ledger" element={<StubPage title="Ledger" />} />
                <Route path="risk" element={<ReviewQueuePage />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </AdminGate>
      </ThemeProvider>
    </QueryClientProvider>
  </StrictMode>,
);