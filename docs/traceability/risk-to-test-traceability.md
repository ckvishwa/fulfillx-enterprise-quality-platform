# Risk-to-Test Traceability

Live mapping from `docs/business-risks/business-risk-register.md` to actual
test files. Updated every phase as tests are added — this is a ledger, not
aspirational.

| Risk ID | Test file | Layer | Status |
|---|---|---|---|
| RISK-02 (duplicate order submission) | `applications/order-service/src/test/java/com/fulfillx/orderservice/order/OrderPersistenceIntegrationTest.java#shouldRejectDuplicateIdempotencyKeyAtTheDatabaseLevel` | Integration (Testcontainers PostgreSQL) | **Implemented** |
| RISK-07 (unauthorized access, foundation only) | `applications/auth-service/src/test/java/com/fulfillx/authservice/auth/AuthApiIntegrationTest.java` — `shouldRejectLoginWithIncorrectPassword`, `shouldRejectLoginForDisabledUser`, `shouldRejectMeWithoutToken`, `shouldRejectMeWithTamperedToken`, `shouldRejectMeWithExpiredToken`, `shouldReturnCurrentUserForValidToken`; `applications/auth-service/src/test/java/com/fulfillx/authservice/security/JwtServiceTest.java` (expiry/tamper at the token layer) | Integration + Unit | **Partially implemented** — proves authentication is real and JWTs can't be forged or reused past expiry; does not yet prove endpoint-specific authorization (no protected business endpoint exists) |
| RISK-15 (invalid state values, partial) | Database `CHECK` constraint `ck_orders_status` in `V1__create_orders_table.sql`; no dedicated test yet beyond implicit coverage via the persistence test | Migration + Integration | **Partially implemented** — values only, not transitions |
| RISK-16 (monetary rounding, partial) | `OrderPersistenceIntegrationTest#shouldPersistOrderAndExposeItThroughFlywayMigratedSchema` asserts integer minor-unit storage round-trips correctly | Integration | **Partially implemented** — storage proven; rounding *rules* not yet exercised |
| N/A — data-integrity backstop for identity (not a numbered business risk; supports RISK-07's foundation) | `AuthApiIntegrationTest#shouldStorePasswordAsSecureHashNotPlaintext`, `#shouldEnforceUniqueEmailConstraintAtTheDatabaseLevel`, `#shouldRejectNonLowercaseEmailAtTheDatabaseLevel`; `OrderPersistenceIntegrationTest#shouldRejectOrderWithoutACustomerIdentityReference` (the practical substitute for a cross-service FK — see ADR-002) | Integration | **Implemented** |
| All other risk IDs (RISK-01, 03, 04, 05, 06, 08–14, 17, 18) | — | — | **Planned** — no test exists yet. Do not claim coverage. |

## How to keep this honest

When a test is added that protects a listed risk, add a row here in the
same commit. When a risk is identified that isn't in the register yet, add
it to `business-risk-register.md` first, then trace the test here. A risk
row with no test file is a gap, not a mistake — it just means the phase
that closes it hasn't happened yet.
