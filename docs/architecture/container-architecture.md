# Container Architecture

**Status: mostly Planned.** Reflects the target architecture from the build
brief (section 7). Only `order-service` (skeleton) and the three
infrastructure containers (PostgreSQL, Redis, Redpanda) currently exist and
run.

```mermaid
flowchart TB
    subgraph clients[Clients]
        webportal[Web Portal<br/>React + TS — Planned]
    end

    subgraph services[Application Services]
        auth[auth-service<br/>Planned]
        inventory[inventory-service<br/>Planned]
        order["order-service<br/>Implemented (skeleton)"]
        payment[payment-service<br/>simulator — Planned]
        notif[notification-consumer<br/>Planned]
    end

    subgraph infra[Infrastructure — Implemented]
        pg[(PostgreSQL 18)]
        redis[(Redis 8)]
        redpanda[[Redpanda]]
    end

    webportal --> auth
    webportal --> order
    order --> inventory
    order --> payment
    order -- publishes events --> redpanda
    redpanda -- consumes --> notif
    order --> pg
    order -. idempotency/locks .-> redis
    auth --> pg
    inventory --> pg
    payment --> pg
```

## Service responsibilities (target)

- **auth-service** — registration, login, password hashing, JWT issuance,
  roles (`CUSTOMER`, `OPERATOR`, `ADMIN`).
- **inventory-service** — products, stock levels, reservation/release,
  atomic stock updates, overselling prevention.
- **order-service** (*only one implemented so far, and only as a skeleton*)
  — order lifecycle orchestration, idempotent creation, coordination with
  inventory/payment, cancellation, refund initiation, shipment state, audit
  history, event publication.
- **payment-service** — deterministic payment simulator: success, decline,
  delay, timeout, "authorized but response lost," duplicate request,
  refund success/failure. Never touches real payment data.
- **notification-consumer** — consumes order events, records simulated
  notifications idempotently, dead-letter handling, restart recovery.

## Data ownership

Each service owns its own tables; no service reaches into another's schema
directly. Cross-service communication is HTTP (synchronous, orchestrated by
order-service) or events over Redpanda (asynchronous). See
`docs/architecture/data-ownership.md`.

## What exists today

- `order-service`: Actuator health only, plus the `orders` table and JPA
  entity. No business endpoints yet.
- `docker-compose.yml`: PostgreSQL 18, Redis 8, Redpanda v26.1.12, each with
  health checks. Validated to start healthy and to be reachable by
  order-service.
