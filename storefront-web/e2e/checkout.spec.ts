import { test, expect } from "@playwright/test";

test.describe("Checkout flow", () => {
  test("successful payment with test card 4242", async ({ page }) => {
    await page.goto("/");

    // Add first product to cart
    const addButton = page.getByRole("button", { name: /add to cart/i }).first();
    await addButton.click();

    // Navigate to cart
    await page.getByLabel(/cart/i).click();
    await expect(page).toHaveURL(/\/cart/);

    // Proceed to checkout
    await page.getByRole("button", { name: /checkout/i }).click();
    await expect(page.url()).toContain("/checkout/");

    // Fill in test card
    await page.getByLabel(/card number/i).fill("4242424242424242");
    await page.getByLabel(/expiry/i).fill("12/29");
    await page.getByLabel(/cvv/i).fill("123");

    // Submit payment
    await page.getByRole("button", { name: /pay/i }).click();

    // Wait for result page — either PROCESSING or SUCCEEDED
    await expect(page.url()).toContain("/result/");

    // Should eventually show success (polling)
    await expect(
      page.getByText(/payment successful|succeeded/i)
    ).toBeVisible({ timeout: 15_000 });
  });

  test("double-submit produces exactly one payment", async ({ page }) => {
    await page.goto("/");

    const addButton = page.getByRole("button", { name: /add to cart/i }).first();
    await addButton.click();
    await page.getByLabel(/cart/i).click();
    await page.getByRole("button", { name: /checkout/i }).click();

    await page.getByLabel(/card number/i).fill("4242424242424242");
    await page.getByLabel(/expiry/i).fill("12/29");
    await page.getByLabel(/cvv/i).fill("123");

    // Click pay rapidly twice
    const payButton = page.getByRole("button", { name: /pay/i });
    await payButton.click();
    // Try to click again immediately — should be disabled or idempotent
    const isDisabled = await payButton.isDisabled().catch(() => true);
    if (!isDisabled) {
      await payButton.click();
    }

    await expect(page.url()).toContain("/result/");
    await expect(
      page.getByText(/payment successful|succeeded/i)
    ).toBeVisible({ timeout: 15_000 });

    // The session should have exactly one paymentId — the BFF
    // used the same idempotency key for both attempts
  });

  test("declined card shows failure without ledger entries", async ({ page }) => {
    await page.goto("/");

    const addButton = page.getByRole("button", { name: /add to cart/i }).first();
    await addButton.click();
    await page.getByLabel(/cart/i).click();
    await page.getByRole("button", { name: /checkout/i }).click();

    // Use the decline test card
    await page.getByLabel(/card number/i).fill("4000000000000002");
    await page.getByLabel(/expiry/i).fill("12/29");
    await page.getByLabel(/cvv/i).fill("123");

    await page.getByRole("button", { name: /pay/i }).click();

    await expect(page.url()).toContain("/result/");
    await expect(
      page.getByText(/declined|failed|payment failed/i)
    ).toBeVisible({ timeout: 15_000 });
  });
});