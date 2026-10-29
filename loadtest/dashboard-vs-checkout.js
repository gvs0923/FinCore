// Load check: does admin dashboard polling degrade checkout latency?
//
// Two back-to-back phases against checkout-bff:
//   baseline  — checkout traffic only
//   contended — same checkout traffic + many admins polling every dashboard tile
// Compare checkout p95 between the two (tagged `phase`). The AGENTS.md
// conventions (rollup tables, non-locking reads, BFF cache) predict ~no delta.
//
//   k6 run loadtest/dashboard-vs-checkout.js
//   BFF=http://localhost:8089 ADMIN_TOKEN=dev-admin-token k6 run ...

import http from "k6/http";
import { check, sleep } from "k6";

const BFF = __ENV.BFF || "http://localhost:8089";
const ADMIN_TOKEN = __ENV.ADMIN_TOKEN || "dev-admin-token";
const PHASE = "1m";

export const options = {
  scenarios: {
    checkout_baseline: {
      executor: "constant-arrival-rate",
      exec: "checkout",
      rate: 5,
      timeUnit: "1s",
      duration: PHASE,
      preAllocatedVUs: 20,
      tags: { phase: "baseline" },
    },
    checkout_contended: {
      executor: "constant-arrival-rate",
      exec: "checkout",
      rate: 5,
      timeUnit: "1s",
      duration: PHASE,
      startTime: PHASE,
      preAllocatedVUs: 20,
      tags: { phase: "contended" },
    },
    // 50 dashboards each polling every tile once per second — far more
    // aggressive than the real 30–60s refetch intervals.
    admin_polling: {
      executor: "constant-vus",
      exec: "adminPoll",
      vus: 50,
      duration: PHASE,
      startTime: PHASE,
      tags: { phase: "contended" },
    },
  },
  thresholds: {
    "http_req_duration{flow:checkout,phase:baseline}": ["p(95)<1000"],
    "http_req_duration{flow:checkout,phase:contended}": ["p(95)<1000"],
    "checks{flow:checkout}": ["rate>0.99"],
  },
};

const JSON_HEADERS = { "Content-Type": "application/json" };

export function checkout() {
  const tags = { flow: "checkout" };
  const created = http.post(
    `${BFF}/v1/checkout/sessions`,
    JSON.stringify({ amount: 1999, currency: "USD", description: "k6 load" }),
    { headers: JSON_HEADERS, tags: { ...tags, name: "create" } },
  );
  if (!check(created, { "session created": (r) => r.status === 201 }, tags)) return;

  const id = created.json("sessionId");
  const paid = http.post(
    `${BFF}/v1/checkout/sessions/${id}/pay`,
    JSON.stringify({ cardNumber: "4242424242424242", cardBrand: "visa", cardLast4: "4242" }),
    { headers: JSON_HEADERS, tags: { ...tags, name: "pay" } },
  );
  check(paid, { "pay accepted": (r) => r.status === 200 }, tags);

  http.get(`${BFF}/v1/checkout/sessions/${id}`, { tags: { ...tags, name: "poll" } });
}

export function adminPoll() {
  const tags = { flow: "admin" };
  const params = { headers: { "X-Admin-Token": ADMIN_TOKEN }, tags };
  const to = new Date().toISOString();
  const from = new Date(Date.now() - 24 * 3600_000).toISOString();
  const range = `from=${from}&to=${to}`;

  const ts = `${BFF}/v1/admin/metrics/timeseries?bucket=hour&${range}&metric=`;
  const res = http.batch([
    ["GET", `${BFF}/v1/admin/overview`, null, params],
    ["GET", `${ts}payment_count`, null, params],
    ["GET", `${ts}request_count`, null, params],
    ["GET", `${ts}ledger_transactions`, null, params],
    ["GET", `${BFF}/v1/admin/integrity/balances`, null, params],
  ]);
  check(res, { "admin 200s": (rs) => rs.every((r) => r.status === 200) }, tags);
  sleep(1);
}
