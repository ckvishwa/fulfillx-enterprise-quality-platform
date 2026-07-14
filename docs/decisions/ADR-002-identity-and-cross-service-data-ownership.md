# ADR-002: Identity and Cross-Service Data Ownership

- **Status:** Accepted
- **Date:** 2026-07-14

## Context

Phase 2A introduces `auth-service` and its `users` table, which
`order-service`'s `orders.customer_id` needs to reference. This is exactly
the situation `docs/architecture/data-ownership.md` anticipated in Phase 0/1
("no cross-service foreign keys, no shared schema") but hadn't yet had to
resolve concretely, since only one service existed at the time.

Two real options existed:

1. Put both services on the same logical database so `orders.customer_id`
   could carry a real `FOREIGN KEY` to `users.id`.
2. Keep the services on separate logical databases, accept that no physical
   FK is possible, and establish integrity a different way.

The Phase 2A brief explicitly asked for this decision to be made and
documented, not defaulted into silently.

## Decision

**Separate logical databases, no physical foreign key.** The shared local
Postgres container now hosts two databases: `fulfillx_orders`
(order-service) and `fulfillx_auth` (auth-service), created via
`infrastructure/postgres/init/01-create-auth-database.sql`. Order-service's
`V2__document_customer_identity_reference.sql` migration adds no
structural constraint — it documents, via `COMMENT ON COLUMN`, that
`orders.customer_id` is an external reference to auth-service's
`users.id`, with no database-level enforcement.

Referential integrity is instead established **at request time**: when
order-service eventually accepts authenticated requests (Phase 2), it will
trust the `sub` claim of a JWT issued by auth-service as proof that the
referenced user exists, rather than checking a database. Auth-service
already only issues a JWT after confirming a user row exists and is
active, so a valid, unexpired JWT is itself the integrity proof.

## Why

- **This preserves independent deployability**, which is the entire point
  of splitting services in the first place. A physical FK across
  service-owned tables is the classic "shared database" anti-pattern: it
  forces synchronized migrations, blocks independently scaling or
  replacing either service's storage, and reintroduces the tight coupling
  microservice boundaries exist to avoid.
- **PostgreSQL cannot enforce a foreign key across databases** within the
  same server instance. Choosing separate logical databases turns "no
  cross-service FK" from a convention someone could accidentally violate
  into a technical impossibility — the safest kind of guarantee.
- **It matches the principle already committed in Phase 0/1**
  (`docs/architecture/data-ownership.md`: "no cross-service foreign keys, no
  shared schema"). Reversing that silently under time pressure from a single
  phase's test requirements would have been exactly the kind of undocumented
  scope/architecture drift `CLAUDE.md` prohibits.

## Consequences accepted

- **No database-level defense** against an order referencing a
  `customer_id` that doesn't actually exist in auth-service. The defense is
  entirely in the request-time JWT check, which doesn't exist as
  enforcement yet either (no order-creation endpoint exists in Phase 2A).
  This is a real, currently-open gap — tracked in `CLAUDE.md`'s Known
  Limitations — closed when Phase 2 adds the authenticated order-creation
  endpoint.
- **`orders.customer_id`'s only enforced integrity check today is
  `NOT NULL`**, proven by
  `OrderPersistenceIntegrationTest#shouldRejectOrderWithoutACustomerIdentityReference`.
  This is deliberately weaker than a real FK and is the honest tradeoff of
  this decision, not an oversight.
- **Local development complexity is slightly higher**: two logical
  databases in one container, an init script that only runs on a fresh
  volume (documented in the script itself and in the validation steps of
  the phase completion report).
- **Shared Postgres superuser credentials across both databases for local
  dev only.** `auth-service` and `order-service` both currently connect
  using the same `fulfillx`/`fulfillx` credentials from `.env.example`.
  Production would provision a distinct least-privilege role per
  service/database (matching `CLAUDE.md`'s "least-privilege DB credentials
  where practical" rule) — deferred here because building real per-service
  role provisioning in Docker Compose adds meaningful complexity for a
  local-only environment where the credentials are never real secrets to
  begin with. This mirrors the same kind of pragmatic call already made in
  ADR-001.

## Alternatives considered and rejected

- **Shared database, real FK.** Rejected: violates the already-committed
  data-ownership principle, recreates the shared-database anti-pattern, and
  the brief's phrasing ("without explaining... consequences") indicated
  this needed active justification, not default convenience.
- **A local `customer_reference` cache table in order-service**, populated
  by consuming a `UserRegistered` event from auth-service, with a real
  local FK to that cache table. This is a legitimate, common pattern
  (read-model replication) but requires event publication/consumption
  infrastructure that Phase 2A explicitly excludes ("Do not begin ...
  event messaging"). Worth revisiting once Phase 7 (event reliability)
  exists — noted in `docs/roadmap/phased-delivery-plan.md`.
