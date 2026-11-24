# Performance & chaos results

Local development only: use the laptop/Colima stack or `kind-fincore`. Do not
point these workloads at a paid cloud environment or production. They append
stub payment, ledger and audit history; never clear Redis counters or delete
financial history to make a result pass. Do not run Playwright or manual
checkouts concurrently with load tests.

| Date | Commit | Env | Scenario | Checkout p95 (baseline) | Checkout p95 (contended) | Error rate | Notes |
|------|--------|-----|----------|------------------------|--------------------------|------------|-------|

No load or chaos results have been recorded yet. Commands below are a manual
runbook, not evidence that acceptance passed.

## Method

Record tool/version, hardware and VM resources, replica counts, tracing sampling
probability, commit (`git rev-parse HEAD`), dirty-tree status, JVM options and
whether the services run on the host, Compose, or kind. Record the exact request
rate, duration, dataset and risk state. Explain what changed between rows.

For the two-minute API comparison, install a recent k6 locally, start the local
stack and run from the repository root:

```sh
k6 run --summary-export=/tmp/fincore-dashboard-summary.json loadtest/dashboard-vs-checkout.js
```

Defaults: BFF `http://localhost:8089`, Keycloak `http://localhost:8180`. For the
nginx edge set `BFF=http://localhost:8080`. The imported `fincore-loadtest` client
uses client credentials and the viewer role; `LOADTEST_CLIENT_SECRET` overrides
the local demo secret. Never enable HTTP debug logging or save access tokens.
The token must last at least 150 seconds; longer tests need token refresh.
Existing Keycloak realms require explicit client reconciliation because imports
do not update them. Compare tagged baseline/contended checkout HTTP p95 and
record both HTTP errors and failed checks, including admin authorization errors.
These HTTP percentiles are not end-to-end payment completion times.

For real-browser Web Vitals, use recent k6 with role/label locators and a local
Chromium installation:

```sh
WEB=http://localhost:8080 k6 run --summary-export=/tmp/fincore-browser-summary.json loadtest/browser-checkout.js
```

Use `WEB=http://127.0.0.1:5173` for Vite. If required, set
`K6_BROWSER_EXECUTABLE_PATH` to your installed Chromium executable. Do not disable
the browser sandbox. The browser test has two VUs for one minute and reports LCP,
CLS and `checkout_terminal_duration` (Pay click to a result heading). Repeated
test PAN/IP velocity can cause REVIEW or FAILED; those are expected shopper
outcomes, not load-generator errors. REVIEW is a completed handoff to a human,
not a settled payment. Exceptions/timeouts fail the checks threshold. Record
outcome mix separately; reaching a failure page does not prove service health.
Only stub test card data is used; no external payment processor is contacted.

## Local chaos acceptance

Prerequisites: provision the local kind stack, install Calico and verify all
workloads are healthy. Confirm `kubectl --context kind-fincore -n fincore get pods`.
Save current replica counts before each fault, run scenarios separately, and
restore those counts afterward. Do not apply these commands to any other context.
These faults interrupt availability; run them only with explicit operator approval.
Do not execute the runbook automatically in CI.

| Scenario | Local fault | Acceptance evidence |
|----------|-------------|---------------------|
| Keycloak down | `kubectl --context kind-fincore -n fincore scale deployment/keycloak --replicas=0` | Anonymous checkout still creates and completes a stub payment. A fresh anonymous admin browser cannot log in and sees a clean failure, not a redirect loop. Existing sessions/tokens can remain valid; they are not a login-outage test. Restore the original replica count and retry login. |
| One payment pod killed mid-load | Select one running payment pod with `kubectl --context kind-fincore -n fincore get pods -l app.kubernetes.io/name=payment-service`, then `kubectl --context kind-fincore -n fincore delete pod <selected-pod> --wait=false` | The Deployment replaces the pod. Retry the same checkout session/Idempotency-Key; query payment attempts and ledger references to prove at most one capture and one journal transaction. A fresh session per iteration does not prove idempotency. Record restart-window errors and recovery time. |
| Kafka paused | `kubectl --context kind-fincore -n fincore scale statefulset/kafka --replicas=0`; restore the original count after observing the fault | Continue submitting stub payments. Record outbox pending count and `fincore_outbox_oldest_age_seconds`; then prove backlog drains after recovery with no duplicate postings. `OutboxStuck` requires age >60s continuously for 5m: a short pause will not fire it. |
| Network isolation | From a pod with ledger labels, request `http://settlement-service:9097/actuator/health` with a 3-second timeout | Connection times out with Calico enforcing policy. Compare a BFF-labelled probe reaching the same app port (any HTTP response proves connectivity); timeout alone could also mean the target is down. Management health is deliberately on 8081, not 9097. |

The JVM image does not promise curl. For the isolation proof, use a disposable
local probe instead of adding diagnostic tools to production images:

```sh
kubectl --context kind-fincore -n fincore run policy-ledger-probe --rm -i --restart=Never --image=curlimages/curl:8.12.1 --labels=app.kubernetes.io/name=ledger-service -- --connect-timeout 3 --max-time 3 http://settlement-service:9097/
kubectl --context kind-fincore -n fincore run policy-bff-probe --rm -i --restart=Never --image=curlimages/curl:8.12.1 --labels=app.kubernetes.io/name=checkout-bff -- --connect-timeout 3 --max-time 3 http://settlement-service:9097/
```

Record each fault start/end, original/restored replica counts, observed statuses,
metric snapshots and trace IDs. Wait for `kubectl ... rollout status` and repeat a
checkout after recovery. The existing Compose Prometheus config targets host-run
management ports; container/kind outbox alert acceptance needs matching scrape
targets and explicit monitoring ingress permissions first. Do not claim alert
acceptance from an unconfigured Prometheus instance.

## Findings

Add one paragraph per surprise, with the before/after change, raw local result
file, and the relevant local Grafana panel or Jaeger trace reference. Separate
observations from hypotheses. Do not invent measurements or publish tokens, PANs,
PII or sensitive trace attributes.

## Frontend checks

From `storefront-web/`, run `npm run build && npm run check:bundle`, then
`npm run lighthouse` with Node 22.19+ and Chrome installed. Lighthouse uses the
production preview at 127.0.0.1:4173, makes three runs and writes local reports.
It needs no backend for the catalogue page. Do not lower thresholds to hide
regressions. Initial static JS measured 131.8 KB shopper / 218.7 KB admin gzip;
budgets are 145 KB / 241 KB (~10% headroom), with shared static imports counted
once per entry and lazy JS/CSS excluded. Only deliberately lower budgets.
`npm test` includes four regression tests for the budget checker.

## Cost-safe CI

New image, Lighthouse and budget jobs run only for public repositories on standard
Ubuntu runners. Private repositories skip them; use local commands instead.
No registry push, cloud deployment, Lighthouse server or temporary-public upload
is configured. Lighthouse reports stay in `.lighthouseci/` on the executing
machine; GitHub artifact uploads and remote build caches are intentionally absent.
Existing JVM/storefront CI jobs are unchanged in scope and may still consume
private-repository Actions minutes under the repository's existing billing plan.
