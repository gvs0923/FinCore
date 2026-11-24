import { expect, test } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { createHash, createHmac, randomUUID } from "node:crypto";

// The session cookie rides along automatically via the project's storageState;
// mutating calls only need the XSRF token from that same session.
async function adminHeaders(request: import("@playwright/test").APIRequestContext) {
  const { cookies } = await request.storageState();
  return { "X-XSRF-TOKEN": cookies.find((c) => c.name === "XSRF-TOKEN")?.value ?? "" };
}

function sql(query: string) {
  return execFileSync("docker", ["exec", "fincore-postgres-1", "psql", "-U", "fincore", "-d", "fincore", "-tAc", query],
    { encoding: "utf8" }).trim();
}

test("admin refunds a captured order with one audited reversing journal", async ({ page, request }) => {
  test.setTimeout(150_000);
  const card = "4242424242424242";
  const fingerprint = createHash("sha256").update(card).digest("hex").slice(0, 16);
  const keys = [`risk:card:${fingerprint}`, ...["127.0.0.1", "0:0:0:0:0:0:0:1", "::1"].map(ip =>
    `risk:ip:${createHmac("sha256", process.env.FINCORE_RISK_PSEUDONYM_KEY ?? "dev-only-pseudonym-key").update("ip\0").update(ip).digest("hex").slice(0, 32)}`)];
  const ttls = keys.map(key => Number(execFileSync("docker", ["exec", "fincore-redis-1", "redis-cli", "TTL", key], { encoding: "utf8" }).trim()));
  expect(ttls.every(ttl => Number.isFinite(ttl) && ttl !== -1)).toBe(true);
  const remaining = Math.max(...ttls);
  if (remaining >= 0) await new Promise(resolve => setTimeout(resolve, (remaining + 1) * 1000));
  const created = await request.post("/v1/checkout/sessions", {
    data: { amount: 10000, currency: "USD", description: "E2E admin refund" },
  });
  expect(created.status()).toBe(201);
  const { sessionId } = await created.json();
  const paid = await request.post(`/v1/checkout/sessions/${sessionId}/pay`, {
    data: { cardNumber: card, cardBrand: "VISA", cardLast4: "4242", deviceId: `e2e-${randomUUID()}`,
      context: { country: "US", timezone: "UTC" } },
  });
  expect(paid.ok()).toBe(true);
  const { paymentId } = await paid.json();
  expect(paymentId).toMatch(/^[0-9a-f-]{36}$/i);
  await expect.poll(() => sql(`SELECT count(*) FROM settlement_captures WHERE payment_id='${paymentId}'`), { timeout: 15000 }).toBe("1");
  await page.goto(`/admin/payments/${paymentId}`);
  await expect(page.getByRole("heading", { name: /Order checkout-/ })).toBeVisible();
  await page.getByRole("textbox", { name: "Refund reason" }).fill("E2E customer request");
  await page.getByRole("button", { name: "Refund payment", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Confirm full refund" })).toBeVisible();
  await page.getByRole("button", { name: "Confirm refund", exact: true }).click();
  await expect(page.getByText(/Refund recorded:/)).toBeVisible();
  await expect.poll(() => sql(`SELECT count(*) FROM ledger_transactions t JOIN settlement_refunds r ON t.reference='refund:' || r.id WHERE r.payment_id='${paymentId}'`), { timeout: 15000 }).toBe("1");
  expect(sql(`SELECT count(*) FROM refund_audit_events WHERE payment_id='${paymentId}' AND actor LIKE 'user:%'`)).toBe("1");
  expect(sql(`SELECT count(*) FROM ledger_transactions WHERE reference='payment:${paymentId}'`)).toBe("1");
});

for (const originalStatus of ["RECONCILED", "PAID_OUT"]) {
test(`admin reconciles and recovers a late refund against a ${originalStatus} batch`, async ({ page, request }) => {
  test.setTimeout(90_000);
  const merchantId = `e2e-settlement-${randomUUID()}`;
  const first = randomUUID(), second = randomUUID();
  const date = new Date();
  date.setUTCDate(date.getUTCDate() - 1);
  const day = date.toISOString().slice(0, 10);
  const events = [first, second].map(paymentId => JSON.stringify({ schemaVersion: 2, status: "CAPTURED",
    paymentId, merchantId, merchantRef: `e2e-${paymentId}`, processorRef: `capture-${paymentId}`,
    amount: 10000, currency: "USD", capturedAt: `${day}T12:00:00Z` })).join("\n") + "\n";
  execFileSync("docker", ["exec", "-i", "fincore-kafka-1", "/opt/kafka/bin/kafka-console-producer.sh",
    "--bootstrap-server", "localhost:9092", "--topic", "payment.captured"], { input: events, timeout: 15000 });
  await expect.poll(() => sql(`SELECT count(*) FROM settlement_captures WHERE merchant_id='${merchantId}'`), { timeout: 15000 }).toBe("2");
  const created = await request.post(`/v1/admin/settlement/batches?${new URLSearchParams({ merchantId, currency: "USD", settlementDate: day })}`, { headers: await adminHeaders(request) });
  expect(created.ok()).toBe(true);
  const batch = await created.json();
  const refunded = await request.post("/v1/admin/settlement/refunds", { headers: await adminHeaders(request),
    data: { paymentId: first, idempotencyKey: randomUUID(), reason: "E2E full refund", actor: "untrusted-client" } });
  expect(refunded.ok()).toBe(true);
  await page.goto(`/admin/settlement/${batch.id}`);
  await page.getByRole("textbox", { name: "Report reference" }).fill(`e2e-report-${batch.id}`);
  await page.getByRole("textbox", { name: "Processor CSV" }).fill(`processorRef,currency,amountMinor,feeMinor\ncapture-${first},USD,10000,320\ncapture-${second},USD,10000,320\n`);
  await page.getByRole("button", { name: "Import processor report" }).click();
  const report = page.getByRole("link", { name: `e2e-report-${batch.id}` });
  await expect(report).toBeVisible();
  await report.click();
  await expect(page.getByRole("heading", { name: "Reconciliation report" })).toBeVisible();
  await expect(page.getByRole("cell", { name: "MATCHED", exact: true })).toHaveCount(2);
  await page.goto(`/admin/settlement/${batch.id}`);
  if (originalStatus === "PAID_OUT") {
    await page.getByRole("button", { name: "Pay out", exact: true }).click();
    await expect(page.getByRole("heading", { level: 1 })).toContainText("PAID_OUT");
  }
  const original = await (await request.get(`/v1/admin/settlement/batches/${batch.id}`, { headers: await adminHeaders(request) })).json();
  const key = randomUUID();
  const refundRequest = { paymentId: second, idempotencyKey: key, reason: "E2E frozen refund" };
  const frozen = await request.post("/v1/admin/settlement/refunds", { headers: await adminHeaders(request), data: refundRequest });
  expect(frozen.ok()).toBe(true);
  const refund = await frozen.json();
  expect(refund.adjustmentBatchId).toMatch(/^[0-9a-f-]{36}$/i);
  const replay = await (await request.post("/v1/admin/settlement/refunds", { headers: await adminHeaders(request), data: refundRequest })).json();
  expect(replay.adjustmentBatchId).toBe(refund.adjustmentBatchId);
  expect(replay.replayed).toBe(true);
  await page.reload();
  const adjustmentLink = page.getByRole("link", { name: `Adjustment ${refund.adjustmentBatchId}` });
  await expect(adjustmentLink).toBeVisible();
  await adjustmentLink.click();
  await expect(page.getByRole("link", { name: "Original settlement batch" })).toHaveAttribute("href", `/admin/settlement/${batch.id}`);
  await page.getByRole("textbox", { name: "Report reference" }).fill(`e2e-adjustment-${refund.id}`);
  await page.getByRole("textbox", { name: "Processor CSV" }).fill(`processorRef,currency,amountMinor,feeMinor\nrefund:${refund.id},USD,-10000,0\n`);
  await page.getByRole("button", { name: "Import processor report" }).click();
  await expect(page.getByRole("button", { name: "Simulate recovery" })).toBeEnabled();
  await page.getByRole("button", { name: "Simulate recovery" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("RECOVERED");
  await expect.poll(() => sql(`SELECT count(*) FROM ledger_transactions WHERE reference='settlement:${refund.adjustmentBatchId}'`), { timeout: 15000 }).toBe("1");
  expect(sql(`SELECT sum(CASE WHEN e.entry_type='DEBIT' THEN e.amount ELSE -e.amount END) FROM ledger_entries e JOIN ledger_transactions t ON t.id=e.transaction_id WHERE t.reference='settlement:${refund.adjustmentBatchId}'`)).toBe("0.0000");
  expect((await request.post(`/v1/admin/settlement/batches/${refund.adjustmentBatchId}/payout`, { headers: await adminHeaders(request) })).status()).toBe(409);
  expect(await (await request.get(`/v1/admin/settlement/batches/${batch.id}`, { headers: await adminHeaders(request) })).json()).toEqual(original);
  if (originalStatus === "RECONCILED") {
    expect((await request.post(`/v1/admin/settlement/batches/${batch.id}/payout`, { headers: await adminHeaders(request) })).ok()).toBe(true);
  }
  await expect.poll(() => sql(`SELECT count(*) FROM ledger_transactions WHERE reference='settlement:${batch.id}'`), { timeout: 15000 }).toBe("1");
  expect(sql(`SELECT sum(CASE WHEN e.entry_type='DEBIT' THEN e.amount ELSE -e.amount END) FROM ledger_entries e JOIN ledger_transactions t ON t.id=e.transaction_id WHERE t.reference='settlement:${batch.id}'`)).toBe("0.0000");
  expect(sql(`SELECT actor FROM refund_audit_events WHERE payment_id='${second}'`)).toMatch(/^user:/);
});
}
