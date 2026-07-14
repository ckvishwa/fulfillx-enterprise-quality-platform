-- Runs once, only when the postgres-data volume is freshly initialized
-- (docker-entrypoint-initdb.d scripts do not re-run against existing data).
--
-- Third logical database, following the same pattern established by
-- 01-create-auth-database.sql: inventory-service gets its own database
-- rather than a shared schema, so PostgreSQL cannot enforce a foreign key
-- across service boundaries even by accident. See
-- docs/decisions/ADR-002-identity-and-cross-service-data-ownership.md.
CREATE DATABASE fulfillx_inventory;
