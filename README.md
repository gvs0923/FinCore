# FinCore — Intelligent Payment & Ledger Platform

FinCore is a production-inspired, event-driven payment infrastructure platform.
It processes payments, maintains financial truth via a double-entry ledger,
uses Kafka for asynchronous workflows, Redis for low-latency operations, and
(later) a hybrid rules/ML risk engine plus an AI operations copilot.

This is a personal learning/portfolio project. No proprietary code, data, or
tooling from any employer is used — only open-source software and publicly
available documentation.

## Status

🚧 Early development — see `docs/architecture/roadmap.md` for the build order.

## Modules (planned)

| Module            | Purpose                                      |
|-------------------|-----------------------------------------------|
| `ledger-service`  | Double-entry ledger, source of financial truth |
| `payment-service` | Payment state machine, idempotency, orchestration |
| `risk-service`    | Fraud/risk rules engine                       |
| `settlement-service` | Merchant settlement batches, refunds, and processor reconciliation (`…settlement.reconciliation` package) |
| `ai-service`      | Python ML risk scoring + LLM explanations (later phase) |

## Local development

Prerequisites: Docker Engine or Docker Desktop (Colima is also supported),
Docker Compose v2, Docker Buildx, Java 21, Gradle 9.7.1, and Node.js 22 LTS
with npm. No company VPN, proxy, or private certificate is required. Keep TLS
verification enabled; public registry certificate errors are an environment
problem, not a project prerequisite. No Gradle wrapper is currently committed.

```bash
docker compose version
docker buildx version
docker compose up -d
gradle build
```

For a fully containerized local demo:

```bash
docker compose --profile apps up -d --build
```

Open `http://localhost:8080`. Demo admin logins are `charan` / `12345678`
(viewer), `admin` / `admin` (finance), `analyst` / `analyst`, and
`nobody` / `nobody` (no admin role). These are local test accounts only.
On Apple Silicon with an affected Colima VM, set
`FINCORE_JVM_EXTRA=-XX:UseSVE=0` in your untracked local `.env`.
Do not recreate an existing Keycloak container without backing up its state:
its local database is container-local, and realm imports do not update an
existing realm. Never use `docker compose down -v` to upgrade a local stack.

For host development, start each service's executable JAR with Java 21 after
`gradle build`, then run `npm ci && npm run dev` in `storefront-web` and open
`http://127.0.0.1:5173`. Stop running JARs before rebuilding them.

Verification:

```bash
gradle test --rerun-tasks
```

From `storefront-web`, run
`npm run typecheck && npm run lint && npm test && npm run build && npm run check:bundle`.
Run `npm run test:e2e` with the backend and local infrastructure running;
these tests append demo financial history. Do not run manual checkouts concurrently.

### Runtime notes

- **Colima + Testcontainers**: `build.gradle` auto-detects the local Colima
  socket only when `DOCKER_HOST` is unset. Other systems use standard
  Testcontainers discovery. Colima's Ryuk workaround does not apply to
  standard Docker installations.
- **Kubernetes is optional**: install kind and kubectl, use the local
  `kind-fincore` context, install a NetworkPolicy-enforcing CNI, and follow
  `docs/performance/README.md`. Do not apply this demo to a remote cluster.
  Compose and kind cannot both own local ports 8080 and 8180.
- **Account balance provisioning**: `account_balances` rows must be created
  (at zero) when an account is created, not lazily on first posting —
  see ADR-001.

## Documentation

- [Phase 4: Risk service architecture guide](docs/ARCHITECTURE-GUIDE-risk-service.md) — rules, payment integration, review workflow, outage policy, design decisions, and verification
- `docs/architecture/` — system design docs
- `docs/decisions/` — Architecture Decision Records (ADRs)
- `docs/reliability/` — failure scenarios, disaster recovery
- `docs/performance/` — load test results
- `docs/security/` — threat model
