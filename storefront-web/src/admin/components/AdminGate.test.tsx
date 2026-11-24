import { act, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, expect, it, vi } from "vitest";
import AdminGate from "./AdminGate";
import { ADMIN_SESSION_EXPIRED, fetchMe } from "../api/adminClient";

vi.mock("../api/adminClient", async (original) => ({
  ...await original<typeof import("../api/adminClient")>(),
  fetchMe: vi.fn(),
  login: vi.fn(),
}));

beforeEach(() => vi.clearAllMocks());

it("removes privileged content and cached data after session expiry", async () => {
  vi.mocked(fetchMe).mockResolvedValue({ name: "operator", roles: ["VIEWER"] });
  const client = new QueryClient();
  client.setQueryData(["orders"], ["private cached order"]);
  render(<QueryClientProvider client={client}><AdminGate><p>Private dashboard</p></AdminGate></QueryClientProvider>);
  await screen.findByText("Private dashboard");
  act(() => window.dispatchEvent(new Event(ADMIN_SESSION_EXPIRED)));
  expect(screen.queryByText("Private dashboard")).not.toBeInTheDocument();
  await screen.findByRole("button", { name: "Sign in" });
  await waitFor(() => expect(client.getQueryData(["orders"])).toBeUndefined());
});
