import { test, expect } from "@playwright/test";
import { execFileSync, execSync } from "node:child_process";
import { createHash } from "node:crypto";

const BROKER_CONTAINER = "fincore-kafka-1";

// The session cookie rides along automatically via the project's storageState;
// mutating calls only need the XSRF token from that same session.
async function adminHeaders(request: import("@playwright/test").APIRequestContext) {
  const { cookies } = await request.storageState();
  return { "X-XSRF-TOKEN": cookies.find((c) => c.name === "XSRF-TOKEN")?.value ?? "" };
}

function financialState(sessionId: string) {
  expect(sessionId).toMatch(/^[0-9a-f-]{36}$/i);
  const sql = `WITH selected AS (SELECT * FROM payments WHERE merchant_ref='checkout-${sessionId}'),
    journals AS (SELECT t.id FROM ledger_transactions t JOIN selected p ON t.reference='payment:' || p.id)
    SELECT (SELECT count(*) FROM selected),
      (SELECT count(DISTINCT idempotency_key) FROM selected),
      (SELECT string_agg(status, ',' ORDER BY status) FROM selected),
      (SELECT count(*) FROM payment_attempts a JOIN selected p ON a.payment_id=p.id WHERE a.status='CAPTURED'),
      (SELECT count(*) FROM journals),
      (SELECT count(*) FROM ledger_entries e JOIN journals j ON e.transaction_id=j.id);`;
  return execFileSync("docker", ["exec", "fincore-postgres-1", "psql", "-U", "fincore", "-d", "fincore", "-tAc", sql],
    { encoding: "utf8" }).trim();
}

async function waitForRiskWindow() {
  const fingerprint = createHash("sha256").update("4242424242424242").digest("hex").slice(0, 16);
  const keys = [`risk:card:${fingerprint}`, "risk:ip:127.0.0.1", "risk:ip:0:0:0:0:0:0:0:1", "risk:ip:::1"];
  const ttls = keys.map((key) => Number(execSync(
    `docker exec fincore-redis-1 redis-cli TTL '${key}'`, { encoding: "utf8" },
  ).trim()));
  expect(ttls.every(Number.isFinite)).toBe(true);
  expect(ttls.every((ttl) => ttl !== -1)).toBe(true);
  const remaining = Math.max(...ttls);
  if (remaining >= 0) await new Promise((resolve) => setTimeout(resolve, (remaining + 1) * 1000));
}

test.describe("Checkout flow", () => {
  test.beforeAll(async () => {
    test.setTimeout(90_000);
    await waitForRiskWindow();
  });
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

    const sessionId = page.url().split("/checkout/")[1];
    let submissions = 0;
    let traceId = "";
    await page.route(`**/v1/checkout/sessions/${sessionId}/pay`, async (route) => {
      traceId = route.request().headers()["traceparent"]?.split("-")[1] ?? "";
      const responses = await Promise.all([route.fetch(), route.fetch()]);
      submissions += responses.length;
      for (const response of responses) expect(response.ok()).toBe(true);
      await route.fulfill({ response: responses[0] });
    });

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
    expect(submissions).toBeGreaterThanOrEqual(2);
    const session = await (await page.request.get(`/v1/checkout/sessions/${sessionId}`)).json();
    expect(session.status).toBe("SUCCEEDED");
    expect(session.paymentId).toMatch(/^[0-9a-f-]{36}$/i);
    await expect.poll(() => financialState(sessionId), { timeout: 15_000 })
      .toBe("1|1|CAPTURED|1|1|2");
    expect(traceId).toMatch(/^[0-9a-f]{32}$/);
    expect(execFileSync("docker", ["exec", "fincore-postgres-1", "psql", "-U", "fincore", "-d", "fincore", "-tAc",
      `SELECT split_part(traceparent, '-', 2) FROM outbox_events WHERE aggregate_id='${session.paymentId}' AND event_type='payment.captured'`],
      { encoding: "utf8" }).trim()).toBe(traceId);
  });

  test("declined card shows failure without ledger entries", async ({ page }) => {
    await page.goto("/");

    const addButton = page.getByRole("button", { name: /add to cart/i }).first();
    await addButton.click();
    await page.getByLabel(/cart/i).click();
    await page.getByRole("button", { name: /checkout/i }).click();

    await expect(page).toHaveURL(/\/checkout\//);
    const sessionId = page.url().split("/checkout/")[1];

    // Use the decline test card
    await page.getByLabel(/card number/i).fill("4000000000000002");
    await page.getByLabel(/expiry/i).fill("12/29");
    await page.getByLabel(/cvv/i).fill("123");

    await page.getByRole("button", { name: /pay/i }).click();

    await expect(page).toHaveURL(/\/result\//);
    await expect(
      page.getByText(/declined|failed|payment failed/i)
    ).toBeVisible({ timeout: 15_000 });
    const session = await (await page.request.get(`/v1/checkout/sessions/${sessionId}`)).json();
    expect(session.status).toBe("FAILED");
    await expect.poll(() => financialState(sessionId)).toBe("1|1|DECLINED|0|0|0");
    await page.waitForTimeout(2_000);
    expect(financialState(sessionId)).toBe("1|1|DECLINED|0|0|0");
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

test.describe("analyst authorization", () => {
  test.use({ storageState: "e2e/.auth/analyst.json" });

test("analyst review updates the shopper and removes resolved payments", async ({ page, context, request, playwright, baseURL }) => {
  test.setTimeout(120_000);
  await waitForRiskWindow();
  // Anonymous probes need their own context — `request` carries the finance session.
  const anon = await playwright.request.newContext({ baseURL, storageState: { cookies: [], origins: [] } });
  expect((await anon.get("/v1/admin/risk/queue")).status()).toBe(401);
  // No session and no XSRF token: the CSRF filter rejects before authz.
  expect((await anon.post(`/v1/admin/risk/${crypto.randomUUID()}/resolve?action=approve`)).status()).toBe(403);
  await anon.dispose();
  const adminPage = await context.newPage();
  adminPage.on("dialog", (dialog) => dialog.accept());

  for (const action of ["approve", "reject"] as const) {
    const created = await request.post("/v1/checkout/sessions", {
      data: { amount: 1_000_001, currency: "USD", description: `E2E ${action}` },
    });
    expect(created.status()).toBe(201);
    const session = await created.json();
    const paid = await request.post(`/v1/checkout/sessions/${session.sessionId}/pay`, {
      data: {
        cardNumber: "4242424242424242", cardBrand: "VISA", cardLast4: "4242",
        deviceId: `e2e-${crypto.randomUUID()}`,
        context: { country: "US", timezone: "UTC" },
      },
    });
    expect(paid.ok()).toBe(true);
    const payment = await paid.json();
    expect(payment.status).toBe("REVIEW");
    const counts = () => execSync(
      `docker exec fincore-postgres-1 psql -U fincore -d fincore -tAc "SELECT (SELECT count(*) FROM payment_attempts WHERE payment_id='${payment.paymentId}' AND status='CAPTURED'),(SELECT count(*) FROM ledger_transactions WHERE reference='payment:${payment.paymentId}')"`,
      { encoding: "utf8" },
    ).trim();
    expect(counts()).toBe("0|0");
    await page.goto(`/result/${session.sessionId}`);
    await expect(page.getByRole("heading", { name: "Order pending review" })).toBeVisible();
    await adminPage.goto("/admin/risk");
    const row = adminPage.getByRole("row").filter({ hasText: payment.paymentId });
    await expect(row).toBeVisible();
    await row.locator("summary").click();
    await expect(row.locator("pre")).toContainText("highAmount");
    await row.getByRole("button", { name: action === "approve" ? "Approve" : "Reject", exact: true }).click();
    await expect(row).toHaveCount(0);
    await expect(page.getByRole("heading", { name: action === "approve" ? "Payment succeeded" : "Payment failed" })).toBeVisible();
    await expect.poll(counts).toBe(action === "approve" ? "1|1" : "0|0");
    const repeated = await request.post(`/v1/admin/risk/${payment.paymentId}/resolve?action=${action}`,
      { headers: await adminHeaders(request) });
    expect(repeated.status()).toBe(409);
    await adminPage.reload();
    await expect(adminPage.getByRole("row").filter({ hasText: payment.paymentId })).toHaveCount(0);
  }

  const overview = await request.get("/v1/admin/overview");
  expect(overview.ok()).toBe(true);
  expect(Array.isArray((await overview.json()).risk.scoreDistribution)).toBe(true);
  await adminPage.goto("/admin");
  await expect(adminPage.getByRole("heading", { name: "Risk · last 24 hours" })).toBeVisible();
  await adminPage.close();
});
});

test("development harness visibly escalates velocity to a risk block", async ({ page }) => {
  test.setTimeout(120_000);
  await waitForRiskWindow();
  await page.goto("/dev/risk");
  await page.getByRole("button", { name: "Fire 10", exact: true }).click();
  await expect(page.locator("pre")).toContainText("#10:", { timeout: 30_000 });
  await expect(page.locator("pre")).toContainText("SUCCEEDED");
  await expect(page.locator("pre")).toContainText("REVIEW");
  await expect(page.locator("pre")).toContainText("FAILED (RISK_BLOCK)");
});