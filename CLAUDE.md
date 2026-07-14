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
| JWT | jjwt (`io.jsonwebtoken`) 0.13.0, modular (`jjwt-api`/`jjwt-impl`/`jjwt-jackson`) | Maintained, HS256, builder/parser API (`Jwts.builder()...signWith(key)`, `Jwts.parser().verifyWith(key)...parseSignedClaims(...)`). See `docs/decisions/ADR-002-identity-and-cross-service-data-ownership.md` for how identity crosses service boundaries. |
| JSON (auth-service manual serialization) | Jackson **3** (`tools.jackson.databind.json.JsonMapper`) | Spring Boot 4's actual default — `spring-boot-starter-web` pulls Jackson 3 via `spring-boot-starter-jackson`, not classic `com.fasterxml.jackson` `ObjectMapper`, which is only present transitively (runtime scope, via `jjwt-jackson`) and not safe to `@Autowire`. Only matters where JSON is written by hand outside normal Spring MVC dispatch (e.g. `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`) — everything returned from a `@RestController`/`@RestControllerAdvice` method is serialized automatically and doesn't need this. |
| MockMvc test support | `org.springframework.boot:spring-boot-webmvc-test` | Spring Boot 4 moved `@AutoConfigureMockMvc` out of `spring-boot-test-autoconfigure` into this module (new package `org.springframework.boot.webmvc.test.autoconfigure`); `spring-boot-starter-test` alone no longer provides it. |
| Quality frameworks | REST Assured, JUnit 5, AssertJ, Jackson, Allure (Java); Playwright + TypeScript (UI); Pact (contracts); k6 (performance) | Per section 6 of the original build brief. Not yet built — see Known limitations. |

Money is always stored as integer minor units (`*_minor` columns, `long` in
Java). Never floating point.

## 4. Repository structure

```
fulfillx-enterprise-quality-platform/
├── applications/
│   ├── order-service/        # Implemented (skeleton). Others: Planned.
│   └── auth-service/         # Implemented: register/login/me, JWT, RBAC foundation.
├── quality-platform/          # Planned — not yet created (Phase 3+)
├── infrastructure/
│   └── postgres/init/         # Implemented: creates the fulfillx_auth database
│                               # (docker-compose otherwise lives at root per
│                               # section 14 of the brief)
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
- Roles: `CUSTOMER`, `OPERATOR`, `ADMIN` — **implemented** in auth-service
  (`UserRole` enum, DB `CHECK` constraint, JWT `role` claim). Public
  registration always creates `CUSTOMER`; there is no endpoint yet that
  lets a caller self-assign `OPERATOR`/`ADMIN`.
- auth-service specifics (Phase 2A): BCrypt password hashing
  (`BCryptPasswordEncoder`); JWT secret required from `AUTH_JWT_SECRET`
  with no hard-coded default (startup fails fast if unset); bounded JWT
  lifetime (`AUTH_JWT_EXPIRATION_MINUTES`, default 30); login checks the
  password before account status, so a caller without valid credentials
  can never learn whether an account exists, is locked, or is disabled;
  password hashes are never returned by any API response and never logged;
  JWT contents are never logged (only the exception class name is logged
  on a rejected token, at debug level).

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

**Phase 0, the smallest safe slice of Phase 1, and Phase 2A (authentication
and identity foundation) are complete.**

Implemented (Phase 0/1):
- Git repository, `.gitignore`, `.gitattributes`, this file, root README,
  architecture/risk/strategy/traceability/ADR/roadmap docs.
- Root Maven aggregator + Maven Wrapper (bootstraps Apache Maven 3.9.16;
  no system Maven required).
- `order-service`: Spring Boot 4.1.0 skeleton, Actuator health endpoint
  (liveness/readiness), `orders` table via Flyway V1 migration, `Order`
  JPA entity + `OrderStatus` enum, real Testcontainers-backed integration
  tests (migration applies cleanly; idempotency-key uniqueness and
  `customer_id NOT NULL` are proven against real PostgreSQL).
- `docker-compose.yml`: PostgreSQL 18, Redis 8, Redpanda v26.1.12, with
  health checks, named volumes, and a shared network.
- `.github/workflows/pr.yml`: Java 21 + Maven-cache setup, `./mvnw clean
  verify` for the full reactor.

Implemented (Phase 2A):
- `auth-service`: Spring Boot 4.1.0, Spring Security (stateless, JWT
  bearer auth), `users` table via Flyway V1 migration in its own
  `fulfillx_auth` database, `User` entity + `UserRole`/`UserStatus` enums.
- Endpoints: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`,
  `GET /api/v1/auth/me`, plus restricted Actuator health.
- JWT issuance/validation (jjwt 0.13.0, HS256, bounded lifetime), BCrypt
  password hashing, correlation-ID propagation (`X-Correlation-Id`,
  request-scoped MDC), the platform error contract enforced consistently
  including at the security-filter layer (not just `@RestControllerAdvice`).
- `docker-compose.yml` now provisions a second logical database
  (`fulfillx_auth`) via `infrastructure/postgres/init/`; order-service and
  auth-service remain on separate databases with no physical cross-service
  foreign key — see
  `docs/decisions/ADR-002-identity-and-cross-service-data-ownership.md`.
- order-service `V2__document_customer_identity_reference.sql`: documents
  (via `COMMENT ON COLUMN`, not a structural change) how `customer_id`
  relates to auth-service's `users.id`.
- 16 new tests in auth-service (13 integration via Testcontainers
  PostgreSQL 18, 3 unit for JWT issue/expire/tamper) + 1 new order-service
  test, all passing. See
  `docs/traceability/risk-to-test-traceability.md` for the mapping.

Not yet built (do not claim otherwise): inventory/payment/notification
services, any order business API beyond `/actuator/health` (order
creation, reservation, payment, cancellation, refund), request-time
JWT-based customer-identity validation in order-service (the ADR-002
integrity mechanism is designed but not wired up — no order-creation
endpoint exists yet to do the validating), the order state-transition
guard logic, an admin/operator endpoint to provision `OPERATOR`/`ADMIN`
accounts or to lock/disable a user, the web portal, the quality-platform
Java/Playwright/Pact/k6 frameworks, the outbox pattern, seeded defects.

## 16. Known limitations

- `order-service` has no order API yet beyond Actuator health.
- `customer_id` has **no physical foreign key by design** — order-service
  and auth-service are on separate databases specifically so this isn't
  possible. Integrity is meant to be established via JWT validation at
  request time, but no order-creation endpoint exists yet to actually do
  that validation, so today `NOT NULL` is the only enforced check. See
  ADR-002.
- Order-state **transition guard logic** (rejecting `DELIVERED → CREATED`
  etc.) is not implemented — only the enum and a DB `CHECK` constraint on
  valid *values* exist so far. Transition rules are domain behavior slated
  for Phase 2.
- No outbox pattern yet — no events are published at all yet, so there is
  nothing to make durable. This must be addressed before Phase 7 (event
  reliability) or documented as a residual limitation if deferred further.
- `order-service` and `auth-service` are not containerized (no
  Dockerfiles) and are not part of `docker-compose.yml`; both currently
  run via `./mvnw spring-boot:run` against the composed infrastructure.
  Containerizing them is future work, likely around Phase 11 (release
  evidence).
- No endpoint exists to provision an `OPERATOR`/`ADMIN` account or to
  lock/disable a user — public registration always creates `CUSTOMER`, and
  the "disabled user" test in `AuthApiIntegrationTest` reaches into the
  repository directly to flip status, since there's no API to do it. This
  is intentional for Phase 2A's scope, not an oversight.
- Once a JWT is issued, it remains valid until natural expiry even if the
  account is disabled afterward — there is no revocation list. The
  mitigation is a short, bounded token lifetime (30 minutes by default),
  not revocation; revisit if that tradeoff stops being acceptable.
- Registration reveals whether an email is already registered (409
  `EMAIL_ALREADY_REGISTERED`), a minor, deliberate email-enumeration
  tradeoff — login, by contrast, is hardened against enumeration (uniform
  `INVALID_CREDENTIALS` regardless of whether the email exists).
- auth-service and order-service share one Postgres superuser credential
  pair in local dev (`fulfillx`/`fulfillx`) rather than distinct
  least-privilege roles per database — a documented local-dev
  simplification (ADR-002), not a production security decision.
- CI (`.github/workflows/pr.yml`) runs `./mvnw clean verify` at the repo
  root, which now builds and tests the full reactor (order-service +
  auth-service) — this has been verified **locally** but the workflow
  itself has not yet actually run on GitHub Actions (no push to a remote
  has triggered it).

## 17. Required validation commands

```bash
./mvnw -B clean verify        # compiles and tests the full reactor (needs Docker)
docker compose config          # validate compose syntax
docker compose up -d           # start Postgres (creates fulfillx_auth on first init), Redis, Redpanda
docker compose ps              # confirm all three report healthy
docker compose down            # stop (add -v only if volumes should be wiped, e.g. to re-trigger the auth-db init script)
```

Note: `infrastructure/postgres/init/` only runs against a **fresh**
`postgres-data` volume. If that volume already exists from before
`fulfillx_auth` was introduced, run `docker compose down -v` once to pick
up the new database before `auth-service` can connect.

## 18. Common development commands

Always use `./mvnw` (the committed Maven Wrapper), never a bare `mvn` — no
system-wide Maven install is assumed or required.

```bash
# Full reactor build + test (what CI runs)
./mvnw -B clean verify

# Build/test a single module only (-am also builds its dependencies)
./mvnw -pl applications/auth-service -am clean verify
./mvnw -pl applications/order-service -am clean verify

# Run a single test class
./mvnw -pl applications/auth-service test -Dtest=JwtServiceTest

# Run a single test method
./mvnw -pl applications/auth-service test -Dtest=AuthApiIntegrationTest#shouldRejectMeWithoutToken

# Run one service locally against docker-compose infra (see section 17
# to start that infra first)
cd applications/order-service && ../../mvnw spring-boot:run   # port 8081
cd applications/auth-service && ../../mvnw spring-boot:run    # port 8083, needs AUTH_JWT_SECRET set (see .env.example)
```

Integration tests in both modules use Testcontainers and therefore need a
running Docker daemon — there is no way to skip that and still exercise
real PostgreSQL behavior (see section 6, "no H2 as the only proof").

No linter, formatter, or static-analysis tool (Checkstyle/Spotless/
SpotBugs/dependency-check) is configured yet, despite being named in the
original brief's tech stack — don't claim one runs in CI or locally until
one is actually added to the POMs.

## 19. Service internals (package map)

Both services follow the same internal shape; once you understand one,
the other reads the same way.

**`order-service`** (`com.fulfillx.orderservice`) — deliberately minimal
right now:
- `order/` — the entire service: `Order` (JPA entity), `OrderStatus`
  (enum — full state space defined, but transition *guards* aren't
  implemented yet, see `docs/architecture/order-lifecycle.md`),
  `OrderRepository`.
- No controllers, no service layer yet — there is no order business API,
  only Actuator health.

**`auth-service`** (`com.fulfillx.authservice`) — the fuller example to
copy patterns from:
- `user/` — persistence: `User` entity, `UserRole`/`UserStatus` enums,
  `UserRepository`.
- `auth/` — business logic: `AuthenticationService` (register/login/
  currentUser) and its exceptions (`EmailAlreadyRegisteredException`,
  `InvalidCredentialsException`, `AccountNotActiveException`); `auth/api/`
  holds the HTTP-facing pieces (`AuthController`, request/response
  records).
- `security/` — JWT issuance/validation (`JwtService`, `JwtProperties`),
  the filter that resolves a principal from a bearer token
  (`JwtAuthenticationFilter`), and the Spring Security wiring
  (`SecurityConfig`, `RestAuthenticationEntryPoint`,
  `RestAccessDeniedHandler` — these last two exist so that authentication/
  authorization failures at the filter-chain level get the same JSON error
  shape as everything else, not Spring Security's default response).
- `web/` — cross-cutting concerns any future service should replicate:
  `CorrelationIdFilter` (+ `CorrelationIdSupport`) propagates
  `X-Correlation-Id` through MDC so it shows up in every log line and
  error response; `ErrorResponse` + `GlobalExceptionHandler` implement the
  platform-wide error contract (see section 8's API design standards in
  the original brief, preserved in ADR-001).

**Cross-service architecture, not visible from either package tree alone**
(see the ADRs for the reasoning):
- `order-service` and `auth-service` use **separate logical PostgreSQL
  databases** (`fulfillx_orders` / `fulfillx_auth`) in the same
  docker-compose container, with **no physical foreign key** between them
  — `orders.customer_id` is only documented (via `COMMENT ON COLUMN` in
  `V2__document_customer_identity_reference.sql`) as referencing
  `auth-service`'s `users.id`. Integrity is meant to be established by
  validating a `auth-service`-issued JWT at request time, not by a
  database constraint (see ADR-002) — this isn't wired up yet because
  order-service has no endpoints to protect.
- Every Flyway migration lives at
  `applications/<service>/src/main/resources/db/migration/`, versioned
  independently per service (each has its own `flyway_schema_history`
  table, in its own database).
