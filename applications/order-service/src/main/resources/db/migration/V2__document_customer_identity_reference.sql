-- V2: Document how orders.customer_id relates to auth-service's identity
-- now that auth-service (and its users table) exists.
--
-- No physical foreign key is added here, and that is a deliberate,
-- documented choice rather than an oversight — see
-- docs/decisions/ADR-002-identity-and-cross-service-data-ownership.md.
-- order-service (fulfillx_orders database) and auth-service
-- (fulfillx_auth database) are separate logical PostgreSQL databases
-- within the shared local-dev Postgres container specifically so that a
-- cross-service foreign key is not just discouraged but technically
-- impossible — PostgreSQL cannot enforce a FOREIGN KEY across databases.
--
-- Referential integrity for customer_id is instead established at request
-- time: order-service trusts the `sub` claim of a JWT issued by
-- auth-service as proof that the referenced user exists, rather than
-- checking a local or remote database on every write. This migration adds
-- no structural change (customer_id is already UUID NOT NULL, indexed,
-- since V1) — it only makes the relationship's design explicit in the
-- schema itself via a column comment, so a future reader inspecting the
-- database directly sees the same reasoning as someone reading the ADR.
--
-- Rollback consideration: purely a comment change; reverting is a no-op
-- forward migration that resets the comment to V1's original text.

COMMENT ON COLUMN orders.customer_id IS
    'External identity reference to auth-service''s users.id (fulfillx_auth database). No physical foreign key by design: order-service and auth-service own independent databases to preserve independent deployability. Referential integrity is established at request time by validating the authenticated principal''s JWT (issued by auth-service), not by a database constraint. See docs/decisions/ADR-002-identity-and-cross-service-data-ownership.md.';
