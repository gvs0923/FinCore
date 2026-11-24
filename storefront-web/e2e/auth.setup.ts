import { expect, test as setup } from "@playwright/test";

export const FINANCE_STATE = "e2e/.auth/finance.json";

for (const [role, username, password] of [
  ["finance", process.env.FINCORE_E2E_FINANCE_USER ?? "admin", process.env.FINCORE_E2E_FINANCE_PASSWORD ?? "admin"],
  ["analyst", process.env.FINCORE_E2E_ANALYST_USER ?? "analyst", process.env.FINCORE_E2E_ANALYST_PASSWORD ?? "analyst"],
  ["viewer", process.env.FINCORE_E2E_VIEWER_USER ?? "charan", process.env.FINCORE_E2E_VIEWER_PASSWORD ?? "12345678"],
]) {
  setup(`sign in as ${role}`, async ({ page }) => {
    await page.goto("/admin");
    await page.getByLabel("Username or email").fill(username);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Sign In", exact: true }).click();
    await expect(page).toHaveURL(/\/admin/);
    await expect(page.getByRole("button", { name: "Sign out", exact: true })).toBeVisible();
    await page.context().storageState({ path: `e2e/.auth/${role}.json` });
  });
}
