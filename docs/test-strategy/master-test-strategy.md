# Master Test Strategy

## Purpose

This platform's test suite exists to protect the risks in
`docs/business-risks/business-risk-register.md`. Test count is never a
goal in itself; the brief's directional target of ~110–140 meaningful tests
is a byproduct of covering real risk, not a quota to hit.

## Layers

| Layer | Tool | What it proves | Status |
|---|---|---|---|
| Unit/domain | JUnit 5 + AssertJ | Domain rules in isolation (e.g. JWT issue/expire/tamper in `JwtServiceTest` in both auth-service and inventory-service; order state-transition guards remain Planned for Phase 2) | **Started** |
| Integration | JUnit 5 + Testcontainers | Real behavior against real PostgreSQL/Redis/Redpanda, not mocks | **Started** — `OrderPersistenceIntegrationTest`, `AuthApiIntegrationTest`, and inventory-service's `ProductApiIntegrationTest`/`InventoryApiIntegrationTest`/`ReservationApiIntegrationTest` all run against real Testcontainers PostgreSQL 18 instances |
| API | REST Assured + JUnit 5 + Allure | Contract-level behavior of each service's HTTP API, including negative/authz cases | Planned (Phase 3) |
| UI | Playwright + TypeScript | Critical user-visible flows, authorization boundaries, accessibility, premature-success prevention | Planned (Phase 5) |
| Contract | Pact | Independently-deployable service boundaries don't break each other | Planned (Phase 6) |
| Event | JUnit 5 + Testcontainers (Redpanda) | Duplicate/out-of-order/poison/restart/eventual-consistency behavior | Planned (Phase 7) |
| Concurrency | JUnit 5 (deterministic concurrency harness) | Inventory races, duplicate submission, payment uncertainty, compensation, restart recovery | **Started** — RISK-01 (inventory overselling) proven by `ConcurrentReservationIntegrationTest` in Phase 2B, ahead of the originally planned Phase 8, since inventory-service's core invariant needed the proof as soon as reservation existed. Duplicate submission/payment uncertainty/compensation/restart concurrency scenarios remain Planned (Phase 8). |
| Performance | k6 | Latency/throughput *and* business correctness under load (e.g. inventory must not go negative even if throughput looks fine) | Planned (Phase 9) |

## Non-negotiable rules

- **No H2 as the sole proof of PostgreSQL behavior.** Established from the
  very first test in this repo.
- **No fixed sleeps for async assertions.** Bounded polling with a timeout
  and a clear expected end state only, once async tests exist (Phase 7+).
- **No test-order dependencies, no shared mutable global state.**
- **Database assertions are targeted, not everywhere** — most tests assert
  at the layer they exercise (API response, event content, etc.); DB-state
  assertions are reserved for tests specifically about data integrity.
- **Every async test needs a timeout and useful failure evidence** once
  those tests exist.
- **A test that only asserts HTTP 200 is not acceptable.** Assert the
  actual business outcome.

## CI integration

`.github/workflows/pr.yml` runs `./mvnw clean verify` at the repo root,
which now builds and tests the **full reactor** (`order-service` +
`auth-service` + `inventory-service` — 3 + 16 + 36 tests respectively as of
Phase 2B), including every Testcontainers-backed integration test. This
has been verified locally; the workflow itself has not yet actually run on
GitHub Actions for this phase's changes. As layers
are added (API, event, concurrency, contract), they join the PR pipeline
per the target sequence in the original build brief (section 24):
formatting → static analysis → unit → build → Testcontainers integration →
API → Pact → critical Playwright → dependency/secret scanning → quality
gate. Heavier suites (full Playwright matrix, concurrency, k6 baseline)
move to a nightly pipeline once they exist — not on every PR.

## Evidence

Allure reporting is planned alongside the API automation framework
(Phase 3). No Allure evidence exists yet; do not claim otherwise.
