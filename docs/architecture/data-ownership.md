# Data Ownership

## Principles

- **PostgreSQL is the authoritative system of record** for all business
  data. Every service that owns business state gets its own tables; no
  cross-service foreign keys, no shared schema. As of Phase 2A this is
  enforced structurally, not just by convention: `order-service` and
  `auth-service` live in separate logical databases
  (`fulfillx_orders`/`fulfillx_auth`) within the same local Postgres
  container, so PostgreSQL itself cannot create a foreign key between
  their tables even by accident. See
  `docs/decisions/ADR-002-identity-and-cross-service-data-ownership.md`.
- **Redis is coordination-only**: idempotency-key locks, short-lived locks,
  retry coordination, controlled caching. It must never be the sole source
  of truth for order or payment state. Redis is not used anywhere yet (no
  service needs it in this phase) — this principle is recorded now so it
  isn't violated later.
- **Money is integer minor units** (`*_minor` columns, `BIGINT` in
  PostgreSQL, `long` in Java). Never floating point, anywhere.
- **Timestamps** are `TIMESTAMPTZ` in PostgreSQL / `Instant` in Java for all
  machine-recorded times.
- **External identifiers are UUIDs** unless a documented reason exists
  otherwise.

## Current ownership (implemented)

| Database | Table | Owning service | Notes |
|---|---|---|---|
| `fulfillx_orders` | `orders` | order-service | V1 migration. `customer_id` has **no physical FK by design** — see ADR-002; V2 migration documents the relationship via `COMMENT ON COLUMN` only. |
| `fulfillx_orders` | `flyway_schema_history` | order-service (Flyway-managed) | Standard Flyway bookkeeping table. |
| `fulfillx_auth` | `users` | auth-service | V1 migration. Normalized unique email, BCrypt `password_hash`, `role`/`status` `CHECK` constraints, optimistic-lock `version`. |
| `fulfillx_auth` | `flyway_schema_history` | auth-service (Flyway-managed) | Independent from order-service's — different database entirely. |

## Target ownership (planned, not yet built)

| Table(s) | Owning service |
|---|---|
| `products`, `inventory_items`, `reservations` | inventory-service |
| `order_line_items`, `shipments`, `audit_entries` | order-service |
| `payment_operations`, `refunds` | payment-service |
| `notifications`, `dead_letter_entries` | notification-consumer |
| `outbox_events` | order-service (if/when the outbox pattern lands — see Known Limitations in `CLAUDE.md`) |

## Cross-service data access

Services never query another service's tables directly. All cross-service
reads/writes go through that service's HTTP API (synchronous) or through
events on Redpanda (asynchronous) — or, for identity specifically, through
a JWT that auth-service issued, which other services can validate locally
(signature + expiry) without calling back to auth-service or its database
at all. This last pattern is designed (see ADR-002) but not yet wired up:
`order-service` doesn't validate JWTs yet because it has no endpoints worth
protecting.
