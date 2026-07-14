-- V1: Establish the orders table.
--
-- Scope: intentionally minimal for Phase 0/1. Line items, shipment detail,
-- and explicit foreign keys to customers/reservations/payments are deferred
-- to the migrations that introduce those services (auth-service does not
-- exist yet, so customer_id has no FK target).
--
-- Rollback consideration: this migration has no destructive counterpart to
-- ship in the same release. If V1 must be reverted before any dependent data
-- exists, drop the table directly; once orders exist, reverting requires a
-- forward-fix migration (e.g. V2) rather than editing this file, per the
-- project's "never edit a released migration" rule.

CREATE TABLE orders (
    id                  UUID PRIMARY KEY,
    customer_id         UUID NOT NULL,
    idempotency_key     VARCHAR(100) NOT NULL,
    status              VARCHAR(30) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    subtotal_minor      BIGINT NOT NULL,
    tax_minor           BIGINT NOT NULL,
    total_minor         BIGINT NOT NULL,
    correlation_id      UUID NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key),

    CONSTRAINT ck_orders_status CHECK (status IN (
        'CREATED', 'INVENTORY_RESERVED', 'INVENTORY_REJECTED',
        'PAYMENT_AUTHORIZED', 'PAYMENT_FAILED', 'CONFIRMED',
        'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED',
        'INVENTORY_RELEASED', 'REFUND_PENDING', 'REFUNDED'
    )),

    CONSTRAINT ck_orders_nonnegative_amounts CHECK (
        subtotal_minor >= 0 AND tax_minor >= 0 AND total_minor >= 0
    ),

    CONSTRAINT ck_orders_currency_format CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_correlation_id ON orders (correlation_id);
CREATE INDEX idx_orders_status ON orders (status);

COMMENT ON TABLE orders IS 'Authoritative order records. PostgreSQL is the system of record; see docs/architecture/data-ownership.md.';
COMMENT ON COLUMN orders.idempotency_key IS 'Protects against duplicate order submission; see docs/business-risks/business-risk-register.md.';
COMMENT ON COLUMN orders.subtotal_minor IS 'Integer minor units (cents). Never floating point.';
