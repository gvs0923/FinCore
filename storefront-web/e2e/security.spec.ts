import { expect, test } from "@playwright/test";

for (const role of ["viewer", "finance", "analyst"]) {
  test.describe(`${role} security boundary`, () => {
    test.use({ storageState: `e2e/.auth/${role}.json` });

    test("enforces roles, protects mutations and serves CSP", async ({ page, request }) => {
      const response = await page.goto("/admin/settlement");
      const csp = response?.headers()["content-security-policy"];
      expect(csp).toContain("script-src 'self'");
      expect(csp).not.toContain("'unsafe-inline'");
      const create = page.getByRole("button", { name: "Create batch", exact: true });
      if (role === "finance") await expect(create).toBeEnabled();
      else await expect(create).toBeDisabled();
      await page.goto("/admin/risk");
      const approvals = page.getByRole("button", { name: "Approve", exact: true });
      if (role !== "analyst") {
        for (const button of await approvals.all()) await expect(button).toBeDisabled();
      }
      const { cookies } = await request.storageState();
      const headers = { "X-XSRF-TOKEN": cookies.find(c => c.name === "XSRF-TOKEN")?.value ?? "" };
      if (role !== "finance") {
        expect((await request.post(`/v1/admin/settlement/batches/${crypto.randomUUID()}/payout`, { headers })).status()).toBe(403);
      }
      if (role !== "analyst") {
        expect((await request.post(`/v1/admin/risk/${crypto.randomUUID()}/resolve?action=approve`, { headers })).status()).toBe(403);
      }
      const missingCsrf = await request.post(`/v1/admin/settlement/batches/${crypto.randomUUID()}/payout`);
      expect(missingCsrf.status()).toBe(403);
      expect((await missingCsrf.json()).code).toBe("CSRF_TOKEN_INVALID");
    });
  });
}

test.describe("independent logout session", () => {
  test.use({ storageState: { cookies: [], origins: [] } });
  test("logout ends the local session and returns to the public origin", async ({ page, baseURL }) => {
    await page.goto("/admin");
    await page.getByLabel("Username or email").fill(process.env.FINCORE_E2E_FINANCE_USER ?? "admin");
    await page.getByLabel("Password", { exact: true }).fill(process.env.FINCORE_E2E_FINANCE_PASSWORD ?? "admin");
    await page.getByRole("button", { name: "Sign In", exact: true }).click();
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await expect(page.getByLabel("Username or email")).toBeVisible();
    expect((await page.request.get(`${baseURL}/v1/admin/me`)).status()).toBe(401);
  });
});
