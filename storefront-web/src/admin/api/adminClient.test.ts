import { afterEach, describe, expect, it, vi } from "vitest";
import { ADMIN_SESSION_EXPIRED, AdminAuthError, AdminForbiddenError, fetchMe, takeReturnPath } from "./adminClient";

const fetchMock = vi.fn();
afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
  sessionStorage.clear();
});

describe("admin session handling", () => {
  it("notifies the gate when a request returns 401", async () => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockResolvedValue(new Response("{}", { status: 401 }));
    const expired = vi.fn();
    window.addEventListener(ADMIN_SESSION_EXPIRED, expired, { once: true });
    await expect(fetchMe()).rejects.toBeInstanceOf(AdminAuthError);
    expect(expired).toHaveBeenCalledOnce();
  });

  it("distinguishes stale CSRF from insufficient permissions", async () => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({
      code: "CSRF_TOKEN_INVALID", message: "Session token missing or expired; reload the page",
    }), { status: 403 }));
    await expect(fetchMe()).rejects.toThrow("reload the page");
    fetchMock.mockResolvedValueOnce(new Response('{"code":"FORBIDDEN"}', { status: 403 }));
    await expect(fetchMe()).rejects.toBeInstanceOf(AdminForbiddenError);
  });

  it("accepts only admin return paths and consumes them once", () => {
    for (const path of ["//untrusted.example", "https://untrusted.example", "/cart", "/administrator"]) {
      sessionStorage.setItem("fincore-admin-return", path);
      expect(takeReturnPath()).toBeNull();
    }
    sessionStorage.setItem("fincore-admin-return", "/admin/risk?pending=true");
    expect(takeReturnPath()).toBe("/admin/risk?pending=true");
    expect(takeReturnPath()).toBeNull();
  });
});
