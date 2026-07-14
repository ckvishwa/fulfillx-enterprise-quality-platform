-- V1: Establish the users table.
--
-- Lives in the fulfillx_auth database (separate from order-service's
-- fulfillx_orders database — see
-- docs/decisions/ADR-002-identity-and-cross-service-data-ownership.md).
-- No other service may reach into this table directly; identity is
-- exposed only through auth-service's API and JWTs it issues.
--
-- Rollback consideration: no destructive counterpart ships alongside this
-- migration. Reverting before any real users exist means dropping the
-- table directly; once users exist, revert via a forward-fix migration
-- rather than editing this file.

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email UNIQUE (email),

    -- Email is normalized (trimmed + lowercased) by the application before
    -- it ever reaches this table. This constraint is a database-layer
    -- backstop against a future bug bypassing that normalization, the same
    -- pattern used for orders.currency in order-service's V1 migration.
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),

    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'OPERATOR', 'ADMIN')),

    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE INDEX idx_users_role ON users (role);

COMMENT ON TABLE users IS 'Authoritative identity records for FulfillX. Owned exclusively by auth-service; see docs/architecture/data-ownership.md.';
COMMENT ON COLUMN users.email IS 'Normalized (trimmed, lowercased) before storage. Unique per ck_users_email_lowercase + uq_users_email.';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash only. Never plaintext, never logged, never returned by the API.';
