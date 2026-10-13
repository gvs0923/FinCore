import { Outlet } from "react-router-dom";
import Header from "./Header";
import Footer from "./Footer";

export default function Layout() {
  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-700 antialiased dark:bg-slate-950 dark:text-slate-300">
      <Header />
      <div className="flex-1">
        <Outlet />
      </div>
      <Footer />
    </div>
  );
}
