# CLAUDE.md — FulfillX Operating Contract

This file is the permanent operating contract for any agent (human or AI)
working in this repository. Read it before making changes. If something here
conflicts with an ad-hoc instruction you've been given, prefer this file
unless a human explicitly overrides it in the current conversation.

## 1. Project mission

FulfillX is a portfolio-grade distributed order-fulfillment system paired
with the quality-engineering platform that validates it. The **primary
product is the quality platform**, not the storefront. Every business
workflow exists to give automated tests something real and risky to protect:
inventory overselling, duplicate payment authorization, payment uncertainty
after timeout, unauthorized refunds, duplicate/out-of-order event delivery,
poison messages, consumer-restart recovery, and API compatibility breaks.

The engineering story this repository must tell is: *risks were identified,
protections were designed across multiple test layers, realistic defects
were reproduced and fixed, and release-quality evidence was automated* — not
"a large number of tests exist."

## 2. Scope lock

**In scope (≈8 workflows):** registration/authentication, RBAC, product &
inventory management, order creation, inventory reservation, payment
authorization, cancellation & inventory release, refund processing, shipment
status, audit history.

**Out of scope for the foreseeable roadmap:** recommendations, marketplace
sellers, advanced search, reviews/ratings, loyalty, wishlists, complex
promotions, multiple real payment providers, real card processing,
Kubernetes, mobile apps, ML features, GraphQL, event sourcing as the primary
persistence model, 50-microservice sprawl, a bespoke design system,
production cloud deployment before local reliability exists.

Do not silently expand scope. If a task seems to require something on the
out-of-scope list, stop and raise it rather than building it.

## 3. Technology choices (do not swap without approval)

| Concern | Choice | Why |
|---|---|---|
| Language/runtime | Java 21 | LTS, required by the brief |
| Backend framework | Spring Boot **4.1.0** | Spring Boot 3.5 reached OSS end-of-life 2026-06-30; 4.1.0 is the only actively supported line (support through 2027-07-31) as of this writing. See `docs/decisions/ADR-001-initial-architecture.md`. |
| Build | Maven, via the committed **Maven Wrapper** (`./mvnw`) | No system-wide Maven was available in the initial environment; the wrapper makes the build reproducible everywhere without requiring a local install. |
| Database | PostgreSQL 18 | System of record for all business data. |
| Cache/coordination | Redis 8 | Idempotency, short-lived locks, retry coordination only — never sole source of truth for order/payment state. |
| Event streaming | Redpanda (Kafka API-compatible) | Preferred over Kafka for local developer ergonomics. |
| Migrations | Flyway, via `spring-boot-starter-flyway` + `flyway-database-postgresql` | Spring Boot 4.x no longer auto-configures Flyway from `flyway-core` alone; the starter is mandatory. PostgreSQL support was split out of `flyway-core` in Flyway 10+. |
| Integration testing | Testcontainers **2.x** | Note: Testcontainers 2.0 renamed several artifacts (e.g. `org.testcontainers:junit-jupiter` → `org.testcontainers:testcontainers-junit-jupiter`, `org.testcontainers:postgresql` → `org.testcontainers:testcontainers-postgresql`). Always check the locally-resolved `testcontainers-bom` before assuming pre-2.x coordinates. |
| Quality frameworks | REST Assured, JUnit 5, AssertJ, Jackson, Allure (Java); Playwright + TypeScript (UI); Pact (contracts); k6 (performance) | Per section 6 of the original build brief. Not yet built — see Known limitations. |

Money is always stored as integer minor units (`*_minor` columns, `long` in
Java). Never floating point.

## 4. Repository structure

```
fulfillx-enterprise-quality-platform/
├── applications/
│   └── order-service/        # Implemented (skeleton). Others: Planned.
├── quality-platform/          # Planned — not yet created (Phase 3+)
├── infrastructure/            # Not yet needed as a separate tree; compose
│                               # lives at root per section 14 of the brief
├── docs/
│   ├── architecture/
│   ├── business-risks/
│   ├── test-strategy/
│   ├── traceability/
│   ├── decisions/
│   └── roadmap/
├── defects/                   # Planned — no seeded defects yet (Phase 10)
├── .github/workflows/
├── docker-compose.yml
├── .env.example
├── CLAUDE.md
├── README.md
└── pom.xml                    # Reactor aggregator, not a Spring parent
```

**Deviation from the brief's suggested tree, documented:** `applications/`,
`quality-platform/`, and `defects/` are intentionally **not** pre-created as
empty directories for services/modules that don't exist yet
(`auth-service`, `inventory-service`, `payment-service`,
`notification-consumer`, `web-portal`, every `quality-platform/*` subtree,
every `defects/FX-*` folder). Creating empty placeholder folders and
describing them as scaffolded modules is an explicitly prohibited shortcut
(section 32 of the brief). Each of those appears in this repo's tree the
same phase it gets real content.

The root `pom.xml` is a plain aggregator (`packaging=pom`) — it is **not**
the Maven `<parent>` for `order-service`. `order-service` inherits directly
from `spring-boot-starter-parent`, because future `quality-platform/*`
modules (REST Assured, Playwright drivers, etc.) are not Spring Boot
applications and must not inherit Spring Boot's dependency management.

## 5. Coding standards

- Clarity over cleverness; thin controllers; domain logic out of
  controllers; dependency injection; no hidden side effects.
- Java: no raw types, no unchecked suppression without a comment explaining
  why, records/enums where they fit, `Instant` for machine timestamps,
  UUIDs for externally visible IDs, integer minor units or scaled
  `BigDecimal` for money — never floating point.
- SQL: explicit constraints and indexes, no implicit float money columns, no
  silent cascades, rollback considerations noted in migration comments.
- Comments explain *why*, never *what* — delete any comment a reasonably
  careful reader wouldn't need.
- No half-finished implementations, no speculative abstractions, no
  placeholder methods "for future use." If it isn't needed yet, don't add
  it.

## 6. Testing standards

- Deterministic setup, isolated data, no test-order dependencies, no shared
  mutable global state, no fixed sleeps for async assertions (bounded
  polling / awaitility-style only).
- Never let H2 be the *only* proof of PostgreSQL-dependent behavior — the
  very first test in this repo (`OrderPersistenceIntegrationTest`) runs
  against a real Testcontainers PostgreSQL instance for exactly this reason.
- Assert meaningful business outcomes, not just HTTP 200 / context-loads.
- Database assertions belong in targeted integrity tests, not everywhere.

## 7. Git safety rules

- Never force-push, never `reset --hard` / discard uncommitted work without
  explicit approval, never amend a commit you didn't just create in the same
  turn, never skip hooks.
- Show staged files before committing; keep commits narrow; use
  conventional, descriptive messages.
- Never commit `.env`, secrets, generated databases, or build output
  (`target/`, `node_modules/`, etc. — see `.gitignore`).
- Never edit an already-released Flyway migration; add a new one instead.

## 8. Security rules

- BCrypt/Argon2 password hashing; JWT signing key from environment
  configuration, never hard-coded; least-privilege DB credentials; no real
  payment data ever, no card storage; Actuator exposure restricted to
  `health,info`; no secrets in logs (passwords, JWT secrets, full tokens,
  PII, card data).
- Roles: `CUSTOMER`, `OPERATOR`, `ADMIN` (not yet implemented — Phase 2A).

## 9. Database rules

- PostgreSQL is the authoritative system of record. Redis is coordination
  only.
- Nonnegative-amount, valid-status, and format constraints are enforced at
  the database layer via `CHECK` constraints, not just application code.
- Unique idempotency keys are a database constraint
  (`uq_orders_idempotency_key`), proven by test, not assumed.

## 10. Event-system rules (Phase 7+, not yet built)

- No fixed sleeps as the primary async synchronization mechanism — bounded
  polling with a timeout and clear expected state only.
- Every async test needs a timeout, a clear expected end state, and useful
  failure evidence.

## 11. CI expectations

- `.github/workflows/pr.yml` currently runs: checkout → Java 21 setup (with
  Maven cache) → `./mvnw clean verify`. This includes the
  Testcontainers-backed integration test, which needs Docker — present by
  default on `ubuntu-latest` GitHub-hosted runners.
- Do not add CI stages that only echo future work. Every stage must run
  something real.

## 12. Definition of done

A feature is done only when: implementation exists, tests exist
(unit + integration where warranted, negative cases included),
authorization is enforced where relevant, errors follow the project's error
contract, correlation/logging is present, a migration exists if schema
changed, docs are updated, the relevant CI stage actually runs it, commands
pass locally, no secrets/build output are committed, the diff was reviewed,
and known limitations are written down honestly.

## 13. HITL approval gates

Do not silently proceed past a phase boundary defined in
`docs/roadmap/phased-delivery-plan.md`. After finishing the scope of a
phase, report status and wait for explicit approval before starting the
next one. Do not replace a chosen technology (see section 3 above) without
approval.

## 14. Prohibited shortcuts

See section 32 of the original build brief (preserved in
`docs/decisions/ADR-001-initial-architecture.md` for reference) — most
importantly: no mock-only "distributed system," no H2-only proof of
Postgres behavior, no fixed-sleep async tests, no disabling security to make
tests pass, no fabricated evidence, no claiming test/feature counts or
capabilities that don't exist in the repo yet.

## 15. Current project phase

**Phase 0 (repository operating system) and the smallest safe slice of
Phase 1 (executable infrastructure foundation) are complete.**

Implemented:
- Git repository, `.gitignore`, `.gitattributes`, this file, root README,
  architecture/risk/strategy/traceability/ADR/roadmap docs.
- Root Maven aggregator + Maven Wrapper (bootstraps Apache Maven 3.9.16;
  no system Maven required).
- `order-service`: Spring Boot 4.1.0 skeleton, Actuator health endpoint
  (liveness/readiness), `orders` table via Flyway V1 migration, `Order`
  JPA entity + `OrderStatus` enum, one real Testcontainers-backed
  integration test (proves the migration applies cleanly and the
  idempotency-key constraint rejects duplicates against real PostgreSQL).
- `docker-compose.yml`: PostgreSQL 18, Redis 8, Redpanda v26.1.12, with
  health checks, named volumes, and a shared network. Validated locally:
  all three containers report healthy, and `order-service` started against
  them, ran Flyway, and answered `/actuator/health` as `UP`.
- `.github/workflows/pr.yml`: Java 21 + Maven-cache setup, `./mvnw clean
  verify` (compiles, runs the integration test, requires Docker — available
  by default on GitHub-hosted runners).

Not yet built (do not claim otherwise): authentication, RBAC,
inventory/payment/notification services, the order state-transition guard
logic (illegal-transition rejection), any HTTP order API beyond
`/actuator/health`, the web portal, the quality-platform Java/Playwright/
Pact/k6 frameworks, the outbox pattern, seeded defects.

## 16. Known limitations

- `order-service` has no order API yet beyond Actuator health — by design
  for this phase (see section 33 of the original brief: "A full order API
  is not required during this first run").
- `customer_id` has no foreign key yet; there is no `users` table because
  auth-service doesn't exist. This is intentional and will be addressed
  when Phase 2A introduces identity.
- Order-state **transition guard logic** (rejecting `DELIVERED → CREATED`
  etc.) is not implemented — only the enum and a DB `CHECK` constraint on
  valid *values* exist so far. Transition rules are domain behavior slated
  for Phase 2.
- No outbox pattern yet — no events are published at all yet, so there is
  nothing to make durable. This must be addressed before Phase 7 (event
  reliability) or documented as a residual limitation if deferred further.
- `order-service` is not containerized (no Dockerfile) and is not part of
  `docker-compose.yml`; it currently runs via `./mvnw spring-boot:run`
  against the composed infrastructure. Containerizing it is future work,
  likely around Phase 11 (release evidence).
- CI runs `order-service`'s own build only; there is nothing else in the
  reactor yet.

## 17. Required validation commands

```bash
./mvnw -B clean verify        # compiles order-service, runs its tests (needs Docker)
docker compose config          # validate compose syntax
docker compose up -d           # start Postgres, Redis, Redpanda
docker compose ps              # confirm all three report healthy
docker compose down            # stop (add -v only if volumes should be wiped)
```
