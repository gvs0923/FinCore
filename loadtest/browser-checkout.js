import { browser } from "k6/browser";
import { check } from "k6";
import { Trend } from "k6/metrics";

const WEB = __ENV.WEB || "http://localhost:8080";
const terminalDuration = new Trend("checkout_terminal_duration", true);

export const options = {
  scenarios: {
    shopper: {
      executor: "constant-vus",
      vus: 2,
      duration: "1m",
      options: { browser: { type: "chromium" } },
    },
  },
  thresholds: {
    browser_web_vital_lcp: ["p(75)<2500"],
    browser_web_vital_cls: ["p(75)<0.1"],
    checks: ["rate>0.95"],
  },
};

export default async function () {
  const page = await browser.newPage();
  try {
    await page.goto(WEB);
    await page.getByRole("button", { name: /add to cart/i }).first().click();
    await page.getByLabel(/cart/i).click();
    await page.getByRole("button", { name: /checkout/i }).click();
    await page.getByLabel(/card number/i).fill("4242".repeat(4));
    await page.getByLabel(/expiry/i).fill("12/29");
    await page.getByLabel(/cvv/i).fill("123");
    const started = Date.now();
    await page.getByRole("button", { name: /^pay\b/i }).click();
    await page.getByRole("heading", {
      name: /^(Payment succeeded|Payment failed|Order pending review)$/,
    }).waitFor({ timeout: 30000 });
    terminalDuration.add(Date.now() - started);
    check(true, { "reached terminal state": () => true });
  } catch (error) {
    check(false, { "reached terminal state": () => false });
    throw error;
  } finally {
    await page.close();
  }
}
