# Data Ownership

## Principles

- **PostgreSQL is the authoritative system of record** for all business
  data. Every service that owns business state gets its own tables; no
  cross-service foreign keys, no shared schema.
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

| Table | Owning service | Notes |
|---|---|---|
| `orders` | order-service | V1 migration. `customer_id` has no FK yet — no `users` table exists (auth-service is Planned). |
| `flyway_schema_history` | order-service (Flyway-managed) | Standard Flyway bookkeeping table. |

## Target ownership (planned, not yet built)

| Table(s) | Owning service |
|---|---|
| `users`, `roles` | auth-service |
| `products`, `inventory_items`, `reservations` | inventory-service |
| `order_line_items`, `shipments`, `audit_entries` | order-service |
| `payment_operations`, `refunds` | payment-service |
| `notifications`, `dead_letter_entries` | notification-consumer |
| `outbox_events` | order-service (if/when the outbox pattern lands — see Known Limitations in `CLAUDE.md`) |

## Cross-service data access

Services never query another service's tables directly. All cross-service
reads/writes go through that service's HTTP API (synchronous) or through
events on Redpanda (asynchronous). This is not yet exercised anywhere in
the codebase since only one service exists.
