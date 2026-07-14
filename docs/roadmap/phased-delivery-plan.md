# Phased Delivery Plan

Each phase ends with an explicit HITL approval gate — the next phase does
not start automatically. See `CLAUDE.md` section 13.

| Phase | Name | Status |
|---|---|---|
| 0 | Repository operating system | **Complete** |
| 1 | Executable infrastructure foundation (smallest safe slice) | **Complete** |
| 2A | Authentication and identity foundation | **Complete** |
| 2 | First complete vertical slice (register → order → payment → confirm → event → audit) | Next (awaiting approval) |
| 3 | API quality framework (REST Assured + JUnit 5 + Allure) | Planned |
| 4 | Real integration platform (expanded Testcontainers coverage) | Planned |
| 5 | Playwright UI layer | Planned |
| 6 | Pact contracts | Planned |
| 7 | Event reliability (duplicate/out-of-order/poison/restart/eventual consistency) | Planned |
| 8 | Concurrency and resilience (inventory race, duplicate submission, payment uncertainty, compensation, restart) | Planned |
| 9 | Performance engineering (k6) | Planned |
| 10 | Seeded-defect portfolio (FX-001 through FX-008) | Planned |
| 11 | Release evidence (immutable builds, ephemeral validation, evidence publication) | Planned |

## Phase 0/1 completion summary

See the phase completion report delivered at the end of this run for the
full executive summary, files created/modified, validation results, known
limitations, and git status. This roadmap file tracks phase status only;
it is not a substitute for that report.

## Phase 2A completion summary

See the phase completion report delivered at the end of that run for the
full executive summary, files created/modified, validation results, known
limitations, and git status. Delivered: `auth-service` (registration,
login, `/me`, JWT issuance/validation, `CUSTOMER`/`OPERATOR`/`ADMIN` role
model, correlation IDs, the platform error contract), a `users` Flyway
migration, a second logical Postgres database (`fulfillx_auth`), an
order-service migration documenting (not structurally linking) the
`customer_id` → identity relationship, ADR-002, and 16 new tests. Not
delivered (intentionally, per the phase's own scope boundary): order
business logic, inventory, payment, event messaging, the web portal, and
the REST Assured framework.

## Phase 2 scope (proposed, not started)

Per the original build brief, Phase 2 ("First complete vertical slice") is
the next phase pending approval. Expected scope: order creation wired to
authenticated auth-service identities (closing the request-time integrity
gap noted in ADR-002 and `CLAUDE.md`'s Known Limitations), product/
inventory basics, payment authorization against the (still to be built)
payment simulator, order confirmation, event publication, and audit
history. This is a proposal for discussion, not a commitment — the actual
Phase 2 slice will be scoped explicitly when that phase begins, following
the same audit-report-propose-wait pattern used for prior phases.
