# ADR-003: Inventory Consistency and Atomic Reservation

- **Status:** Accepted
- **Date:** 2026-07-14

## Context

Phase 2B introduces `inventory-service`, which owns product and inventory
state and must guarantee the platform's most important invariant:
**available inventory can never become negative.** This is RISK-01 in
`docs/business-risks/business-risk-register.md` — inventory overselling
under concurrent orders — and this phase exists specifically to close it
with real, tested protection rather than an assumption.

Two related design questions had to be answered concretely: how does a
reservation decrement stock safely under concurrency, and how does
duplicate reservation submission (a retried client request) get handled
without double-reserving or double-charging stock.

## Why PostgreSQL owns the invariant, not application code

Application-level checks ("read the quantity, check it in Java, then
write") cannot be made safe under concurrency without an external lock,
because two concurrent requests can both read the same pre-decrement
quantity, both pass the check, and both write — overselling by exactly the
number of racing requests. This is true regardless of how carefully the
Java code is written; the race is inherent to read-then-write split across
two round trips. PostgreSQL, by contrast, can make a single UPDATE
statement both check and write atomically, with the database's own
row-level locking serializing concurrent attempts against the same row.
Making PostgreSQL respondsible for the invariant — not Redis, not an
in-process lock, not a distributed lock — follows directly from
`docs/architecture/data-ownership.md`'s existing principle that Postgres is
the authoritative system of record and Redis is coordination-only (and
Redis is explicitly out of scope for this phase; see CLAUDE.md's task
brief).

## Why read-then-write is unsafe (illustrated)

```
Thread A: SELECT available_quantity FROM inventory_items WHERE product_id = X;  -- reads 1
Thread B: SELECT available_quantity FROM inventory_items WHERE product_id = X;  -- reads 1
Thread A: available_quantity (1) >= requested (1) -> proceed
Thread B: available_quantity (1) >= requested (1) -> proceed
Thread A: UPDATE ... SET available_quantity = 0;   -- writes 0
Thread B: UPDATE ... SET available_quantity = 0;   -- writes 0 (should have been -1, silently wrong)
```

Both requests believe they succeeded. Two units were promised out of one
unit of real stock — an oversell, with no error and no trace of the
conflict. This pattern is explicitly prohibited by the phase brief and by
this ADR.

## Selected locking/atomic-update strategy

A single, conditional, atomic UPDATE combines the check and the write:

```sql
UPDATE inventory_items
SET available_quantity = available_quantity - :quantity,
    reserved_quantity = reserved_quantity + :quantity,
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE product_id = :productId
  AND available_quantity >= :quantity;
```

Implemented as a Spring Data `@Modifying @Query` method
(`InventoryItemRepository.reserveAtomically`) that returns the number of
rows updated. The caller (`ReservationWriter.reserve`) inspects that count:
exactly `1` means the reservation succeeded; `0` means the guard clause
failed (insufficient stock) and is surfaced as a controlled
`INSUFFICIENT_INVENTORY` (409) error, never a generic failure. There is no
prior `SELECT` to decide whether to proceed — the database itself makes
that decision as part of the same statement.

This relies on ordinary PostgreSQL row-level locking: the UPDATE statement
acquires an exclusive lock on the matched row for the duration of the
enclosing transaction. A second concurrent UPDATE against the same row
blocks until the first transaction commits (or rolls back), then
re-evaluates the `WHERE` clause against the now-current, committed value —
so the guard clause is never evaluated against stale data. No explicit
`SELECT ... FOR UPDATE`, application-level lock, or Redis lock is needed;
the conditional UPDATE **is** the lock. This is a well-established
PostgreSQL pattern, and was chosen over three alternatives considered
below because it needs no additional infrastructure and keeps the
invariant enforced in exactly one place: the database.

Reservation release uses the same pattern in reverse, guarded by
`reserved_quantity >= :quantity` so a release can never drive
`reserved_quantity` negative either — see
`InventoryItemRepository.releaseAtomically` and
`InventoryReservationRepository.markReleasedAtomically` (the latter guards
the `RESERVED -> RELEASED` status transition itself, so two concurrent
releases of the same reservation can only restore stock once).

## Idempotency strategy

Reservation creation accepts a required `idempotencyKey`. The full
contract, implemented in `ReservationService`:

1. **Unseen key:** perform the atomic reservation (`ReservationWriter.reserve`).
   Success inserts a new `inventory_reservations` row whose
   `idempotency_key` carries a unique constraint
   (`uq_inventory_reservations_idempotency_key`) as the final database-level
   backstop.
2. **Seen key, identical request** (same `orderReference`, `productId`,
   `quantity`): return the original reservation. No new row, no additional
   stock movement — a safe replay.
3. **Seen key, different request data:** rejected as `IDEMPOTENCY_CONFLICT`
   (409). Silently treating this as either a replay or a new reservation
   would both be wrong — a key collision with different data is a client
   bug that must be surfaced, not guessed at.
4. **Race on an unseen key** (two concurrent requests submit the same new
   key simultaneously): both attempt the atomic reservation; the loser's
   insert fails on the unique constraint
   (`DataIntegrityViolationException`), which rolls back that attempt's
   entire transaction — including its stock decrement, so no phantom
   reservation partially exists. `ReservationService` catches that
   exception and re-reads the winner's row, applying the same
   match-or-conflict check as case 2/3 above. The client that "lost" the
   race still gets a correct, well-formed response rather than an
   unexplained 500.

Release is idempotent by construction rather than by a lookup-first
pattern: `ReservationWriter.release` checks the reservation's current
status before attempting the transition. If it is already `RELEASED`
(by this call or a prior one), it is returned as-is with no further
database writes — repeated release requests can never restore stock more
than once. If a concurrent release wins the `RESERVED -> RELEASED`
transition first, this call's own conditional UPDATE affects zero rows,
and it falls back to re-reading and returning the now-released state
rather than erroring.

## Transaction boundary

Every atomic operation and its accompanying ledger write happen inside a
single `@Transactional` method on `ReservationWriter`
(`reserve`/`release`) or `ProductService`/`InventoryService` — never split
across two transactions. Concretely:

- `reserve`: the inventory decrement and the `inventory_reservations`
  insert commit together or not at all.
- `release`: the reservation status flip and the inventory restore commit
  together or not at all.
- `createProduct`: the `products` insert and its zero-quantity
  `inventory_items` row insert commit together, guaranteeing every product
  that exists has a matching inventory row (no upsert logic needed
  anywhere else in the service).

`ReservationWriter` is deliberately a separate Spring bean from
`ReservationService`, which orchestrates the idempotency contract above it
(see the class Javadoc in `ReservationWriter`): `ReservationService` needs
to catch an exception thrown partway through `ReservationWriter`'s
transaction and then issue a *new*, separate read — which only works
correctly across a real Spring AOP proxy boundary. A private
`@Transactional` method invoked via `this` inside the same class would
bypass the proxy and silently run with no transactional semantics at all,
a well-known Spring pitfall this design avoids structurally rather than by
convention.

All three `@Modifying` repository methods used inside these transactions
set `clearAutomatically = true`, evicting Hibernate's first-level cache
after the bulk statement runs. Without it, a `findById` issued later in
the same transaction (e.g. `release`'s final re-read) could return a
stale, pre-update managed entity from the persistence context instead of
reflecting the row the bulk UPDATE just changed.

## Alternatives considered and rejected

- **Pessimistic locking (`SELECT ... FOR UPDATE`) followed by a plain
  UPDATE.** Functionally equivalent in safety to the conditional UPDATE
  chosen, but requires two round trips and an explicit lock-then-check
  discipline that's easy to get wrong in future code (e.g. a developer
  adding a new write path that forgets to lock first). The single
  conditional UPDATE makes the safe path the *only* path — there's no
  unlocked variant to accidentally write.
- **Optimistic locking alone (the existing `version` column, retry on
  conflict).** `version` is kept on every table as a general-purpose
  concurrency/audit aid and Hibernate's own dirty-check safety net, but
  optimistic locking alone would mean 15 of 20 concurrent reservation
  attempts in the required concurrency test would need client-side retry
  logic against `OptimisticLockException`, and "retry until you read
  fresh data telling you to give up" is strictly more complex than a
  single conditional statement that already knows the current committed
  state. It does not remove the need for the atomic guard — it would sit
  on top of it, not replace it.
- **Redis-based distributed lock around the reservation critical
  section.** Explicitly out of scope for this phase (`CLAUDE.md`), and
  would violate `docs/architecture/data-ownership.md`'s principle that
  Redis is coordination-only, never the enforcer of a business invariant
  that Postgres can enforce natively. Introducing a second system of
  record for correctness (even a "just coordination" one) adds an
  operational dependency and a new failure mode (lock service down /
  slow) for no safety benefit over the chosen approach.
- **Application-level in-process lock (e.g. a `synchronized` block or
  `ReentrantLock` keyed by product ID).** Only correct for a single JVM
  instance; the platform's stated architecture assumes services may run
  as multiple instances behind a load balancer eventually, at which point
  an in-process lock provides zero protection across instances. The
  database-level guard is correct regardless of how many
  `inventory-service` instances are running.

## Consequences accepted

- Under heavy contention on the same product row, concurrent reservation
  requests serialize (each waits for the previous transaction's commit)
  rather than executing in parallel. This is the intended behavior for
  the invariant this phase protects, and is proven bounded and correct by
  `ConcurrentReservationIntegrationTest`, not just assumed.
- Reservation and release are only "any authenticated caller" in this
  phase, not restricted to a specific internal service identity — see
  `docs/architecture/trust-boundaries.md` and CLAUDE.md's Known
  Limitations. This is an authorization gap, not a consistency gap: it
  does not weaken the atomicity guarantees described here.
- No compensating saga or cross-service orchestration exists yet
  (order-service does not call inventory-service in this phase) — RISK-05
  (partial workflow completion) remains open until Phase 2/8 wires
  order-service to inventory-service and payment-service.
