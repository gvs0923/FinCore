import { expect, test as setup } from "@playwright/test";

export const FINANCE_STATE = "e2e/.auth/finance.json";

setup("sign in as finance-ops", async ({ page }) => {
  await page.goto("/admin");
  await page.getByLabel("Username or email").fill("finance");
  await page.getByLabel("Password", { exact: true }).fill("finance");
  await page.getByRole("button", { name: "Sign In" }).click();
  await expect(page).toHaveURL(/\/admin/);
  await expect(page.getByRole("status")).toHaveCount(0); // gate resolved => /me succeeded, XSRF cookie set
  await page.context().storageState({ path: FINANCE_STATE });
});
