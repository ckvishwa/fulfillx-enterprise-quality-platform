# Phased Delivery Plan

Each phase ends with an explicit HITL approval gate — the next phase does
not start automatically. See `CLAUDE.md` section 13.

| Phase | Name | Status |
|---|---|---|
| 0 | Repository operating system | **Complete** |
| 1 | Executable infrastructure foundation (smallest safe slice) | **Complete** |
| 2A | Authentication and identity foundation | Next (awaiting approval) |
| 2 | First complete vertical slice (register → order → payment → confirm → event → audit) | Planned |
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

## Phase 2A scope (proposed, not started)

Per the original build brief, Phase 2A ("Authentication and identity
foundation") is the next phase pending approval. Expected scope:
`auth-service` skeleton, user registration, password hashing, JWT issuance,
role model (`CUSTOMER`/`OPERATOR`/`ADMIN`), and the `users` table migration
that finally gives `orders.customer_id` something to reference. This is a
proposal for discussion, not a commitment — the actual Phase 2A slice will
be scoped explicitly when that phase begins, following the same
audit-report-propose-wait pattern used for Phase 0/1.
