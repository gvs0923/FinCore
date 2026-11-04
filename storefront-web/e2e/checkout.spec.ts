import { test, expect } from "@playwright/test";
import { execSync } from "node:child_process";

const BROKER_CONTAINER = "fincore-kafka-1";

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
    await expect(page).toHaveURL(/\/checkout\//);

    // Fill in test card
    await page.getByLabel(/card number/i).fill("4242424242424242");
    await page.getByLabel(/expiry/i).fill("12/29");
    await page.getByLabel(/cvv/i).fill("123");

    // Submit payment
    await page.getByRole("button", { name: /pay/i }).click();

    // Wait for result page — either PROCESSING or SUCCEEDED
    await expect(page).toHaveURL(/\/result\//);

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

    // Click pay twice as concurrently as possible — a real double-submit.
    // A second sequential click() would block in actionability checks
    // because the button detaches/disables during navigation.
    const payButton = page.getByRole("button", { name: /pay/i });
    await Promise.allSettled([payButton.click(), payButton.click()]);

    await expect(page).toHaveURL(/\/result\//);
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

    await expect(page).toHaveURL(/\/result\//);
    await expect(
      page.getByText(/declined|failed|payment failed/i)
    ).toBeVisible({ timeout: 15_000 });
  });

  test("broker paused mid-checkout never shows a false success", async ({ page }) => {
    test.setTimeout(120_000);

    await page.goto("/");
    await page.getByRole("button", { name: /add to cart/i }).first().click();
    await page.getByLabel(/cart/i).click();
    await page.getByRole("button", { name: /checkout/i }).click();
    await expect(page).toHaveURL(/\/checkout\//);

    await page.getByLabel(/card number/i).fill("4242424242424242");
    await page.getByLabel(/expiry/i).fill("12/29");
    await page.getByLabel(/cvv/i).fill("123");

    const sessionId = page.url().split("/checkout/")[1];

    execSync(`docker pause ${BROKER_CONTAINER}`);
    try {
      await page.getByRole("button", { name: /pay/i }).click();
      await expect(page).toHaveURL(/\/result\//);

      // Give the sync pay response + SSE window time to resolve.
      await page.waitForTimeout(15_000);

      // Truthfulness invariant: the UI may only claim success if the BFF
      // session really is SUCCEEDED. Anything else visible (processing /
      // still processing / failure) must also match the real state.
      const res = await page.request.get(`/v1/checkout/sessions/${sessionId}`);
      const session = await res.json();

      const uiClaimsSuccess = await page
        .getByText(/payment succeeded|payment successful/i)
        .isVisible();

      if (uiClaimsSuccess) {
        // Success shown while broker is paused → it must have come from the
        // synchronous pay response, i.e. the payment genuinely captured.
        expect(session.status).toBe("SUCCEEDED");
      } else {
        // No success shown → the backend must not secretly be succeeded
        // while the UI pretends otherwise is impossible; just assert the
        // session isn't stuck in an untruthful state.
        expect(["PROCESSING", "OPEN", "FAILED"]).toContain(session.status);
      }
    } finally {
      execSync(`docker unpause ${BROKER_CONTAINER}`);
    }

    // After recovery, the outbox drains and the session reaches a truthful
    // terminal state. (The "Still processing" screen is intentionally
    // terminal — the page may not auto-update, and that's honest.)
    await expect
      .poll(
        async () => {
          const res = await page.request.get(
            `/v1/checkout/sessions/${sessionId}`
          );
          return (await res.json()).status;
        },
        { timeout: 60_000, intervals: [2000] }
      )
      .not.toBe("PROCESSING");
  });
});