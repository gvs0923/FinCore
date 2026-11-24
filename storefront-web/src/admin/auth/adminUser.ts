import { createContext, useContext } from "react";
import type { AdminUser } from "../api/adminClient";

export const AdminUserContext = createContext<AdminUser | null>(null);

export function useAdminUser(): AdminUser {
  const user = useContext(AdminUserContext);
  if (!user) throw new Error("useAdminUser must be used inside AdminGate");
  return user;
}

export const hasRole = (user: AdminUser, role: string) => user.roles?.includes(role) ?? false;