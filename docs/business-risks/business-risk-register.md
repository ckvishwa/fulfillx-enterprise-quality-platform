# Business Risk Register

Every test layer in this platform must trace back to one of these risks.
Do not add tests that don't protect something listed here — and if a real
risk is found that isn't listed, add it before writing the test.

| ID | Risk | Business impact | Primary protection (planned or implemented) |
|---|---|---|---|
| RISK-01 | Inventory overselling under concurrent orders | Sell stock that doesn't exist; broken fulfillment promises | Atomic conditional DB update (no read-then-write), nonnegative `CHECK` constraints, deterministic concurrency test — **implemented and proven** (`ConcurrentReservationIntegrationTest`: 20 concurrent 1-unit reservation attempts against 5 units of stock yield exactly 5 successes, 15 controlled failures; see `docs/decisions/ADR-003-inventory-consistency-and-atomic-reservation.md`) |
| RISK-02 | Duplicate order submission (client retry) | Customer billed/ordered multiple times for one intent | Idempotency-key unique constraint — **implemented and proven** (`uq_orders_idempotency_key`, see `OrderPersistenceIntegrationTest`). The same pattern is now also implemented for reservation creation in inventory-service (`uq_inventory_reservations_idempotency_key`, see `ReservationApiIntegrationTest`). |
| RISK-03 | Duplicate payment authorization | Customer double-charged | Payment idempotency + retry test (Phase 2/8) |
| RISK-04 | Payment uncertainty after network timeout | Order stuck in ambiguous state; possible double charge or lost payment | Idempotent payment operation IDs, retry-returns-existing-result (Phase 8) |
| RISK-05 | Partial workflow completion (inventory reserved, payment never resolves) | Reserved stock never released; phantom holds | Saga compensation, orphan-reservation test (Phase 2/8) |
| RISK-06 | Database rollback failure mid-workflow | Inconsistent state across tables | Integration tests with targeted DB assertions (Phase 4) |
| RISK-07 | Unauthorized refund access | Financial loss, fraud | RBAC enforcement + API/UI authorization tests (Phase 2A/5) — **partially implemented**: the identity/RBAC foundation (real auth, roles, bounded JWTs) exists and is tested; the first role-gated business endpoints exist in inventory-service (product creation and stock adjustment are `ADMIN`-only, proven by test); endpoint-specific *ownership* authorization (e.g. a customer can't refund another customer's order) can't be proven yet because no refund endpoint exists |
| RISK-08 | Duplicate event processing | Duplicate notifications, duplicate side effects | Consumer idempotency test (Phase 7) |
| RISK-09 | Out-of-order event delivery | Corrupted derived state | Ordering-tolerant consumer design + test (Phase 7) |
| RISK-10 | Poison messages | Consumer crash loop, stalled processing | Dead-letter routing + test (Phase 7) |
| RISK-11 | Consumer restart / crash recovery | Lost or duplicated business effects on restart | Restart-recovery test (Phase 7) |
| RISK-12 | API compatibility failures across services | Breaking a consumer without knowing | Pact contract tests (Phase 6) |
| RISK-13 | Incorrect eventual-consistency behavior | UI/clients see stale or wrong state indefinitely | Bounded-polling convergence tests (Phase 7) |
| RISK-14 | Service timeout/retry handling | Cascading failures, resource exhaustion | Timeout tests, resilience patterns (Phase 8) |
| RISK-15 | Invalid order state transitions | Orders shipped unpaid, refunds double-issued, etc. | Domain guard + unit tests (Phase 2), DB `CHECK` on valid values — **partially implemented** (values only, not transitions) |
| RISK-16 | Tax/monetary rounding errors | Incorrect charges | Integer minor-unit storage — **implemented**; rounding-rule tests (Phase 2/10, FX-006) |
| RISK-17 | UI reports success before backend confirms | Customer trusts a false success state | Playwright backend-postcondition tests (Phase 5, FX-007) |
| RISK-18 | Breaking API response field changes | Silent client breakage | Pact + schema compatibility checks (Phase 6, FX-008) |

## Status legend

- **Implemented** — protection exists in code and is exercised by a passing
  test today.
- **Planned** — protection does not exist yet; phase reference indicates
  when it is scheduled.

RISK-01 and RISK-02 have full implemented protection. RISK-07, RISK-15, and
RISK-16 have partial implemented protection. Everything else is planned.
See `docs/traceability/risk-to-test-traceability.md` for the live mapping
to actual test files as they're added.
