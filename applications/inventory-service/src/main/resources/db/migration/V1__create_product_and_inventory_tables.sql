-- V1: Establish products, inventory_items, and inventory_reservations.
--
-- Lives in the fulfillx_inventory database (separate from order-service's
-- fulfillx_orders and auth-service's fulfillx_auth — see
-- docs/decisions/ADR-002-identity-and-cross-service-data-ownership.md for
-- the precedent this follows). No other service may reach into these
-- tables directly; inventory state is exposed only through
-- inventory-service's API.
--
-- Rollback consideration: no destructive counterpart ships alongside this
-- migration. Reverting before any real data exists means dropping the
-- tables directly; once products/inventory/reservations exist, revert via
-- a forward-fix migration rather than editing this file.

CREATE TABLE products (
    id              UUID PRIMARY KEY,
    sku             VARCHAR(64) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    price_minor     BIGINT NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT true,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_products_sku UNIQUE (sku),

    -- SKU is normalized (trimmed + uppercased) by the application before it
    -- ever reaches this table. This constraint is a database-layer backstop
    -- against a future bug bypassing that normalization — the same pattern
    -- used for auth-service's ck_users_email_lowercase.
    CONSTRAINT ck_products_sku_normalized CHECK (sku = upper(sku) AND btrim(sku) = sku AND sku <> ''),

    CONSTRAINT ck_products_name_nonblank CHECK (btrim(name) <> ''),

    CONSTRAINT ck_products_price_nonnegative CHECK (price_minor >= 0),

    CONSTRAINT ck_products_currency_format CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_products_active ON products (active);

COMMENT ON TABLE products IS 'Authoritative product catalog records. Owned exclusively by inventory-service; see docs/architecture/data-ownership.md.';
COMMENT ON COLUMN products.sku IS 'Normalized (trimmed, uppercased) before storage. Unique per uq_products_sku + ck_products_sku_normalized.';
COMMENT ON COLUMN products.price_minor IS 'Integer minor units. Never floating point.';

CREATE TABLE inventory_items (
    product_id          UUID PRIMARY KEY REFERENCES products (id),
    available_quantity  BIGINT NOT NULL DEFAULT 0,
    reserved_quantity    BIGINT NOT NULL DEFAULT 0,
    version              BIGINT NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_inventory_items_available_nonnegative CHECK (available_quantity >= 0),
    CONSTRAINT ck_inventory_items_reserved_nonnegative CHECK (reserved_quantity >= 0)
);

COMMENT ON TABLE inventory_items IS 'One row per product, created at product-creation time. available_quantity can never go negative — this is the invariant this phase exists to prove. See docs/decisions/ADR-003-inventory-consistency-and-atomic-reservation.md.';
COMMENT ON COLUMN inventory_items.available_quantity IS 'Stock currently sellable. Decremented atomically by reservation, incremented atomically by release or stock adjustment.';
COMMENT ON COLUMN inventory_items.reserved_quantity IS 'Stock held against RESERVED reservations. Never negative; decremented only by release.';

CREATE TABLE inventory_reservations (
    id                UUID PRIMARY KEY,
    order_reference   UUID NOT NULL,
    product_id        UUID NOT NULL REFERENCES products (id),
    quantity          BIGINT NOT NULL,
    status            VARCHAR(20) NOT NULL,
    idempotency_key   VARCHAR(100) NOT NULL,
    correlation_id    VARCHAR(100) NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_inventory_reservations_idempotency_key UNIQUE (idempotency_key),

    CONSTRAINT ck_inventory_reservations_status CHECK (status IN ('RESERVED', 'RELEASED')),

    CONSTRAINT ck_inventory_reservations_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_inventory_reservations_product_id ON inventory_reservations (product_id);
CREATE INDEX idx_inventory_reservations_order_reference ON inventory_reservations (order_reference);
CREATE INDEX idx_inventory_reservations_status ON inventory_reservations (status);

COMMENT ON TABLE inventory_reservations IS 'Reservation ledger. Owned exclusively by inventory-service.';
COMMENT ON COLUMN inventory_reservations.idempotency_key IS 'Protects against duplicate reservation submission; mirrors orders.idempotency_key in order-service (RISK-02 pattern) applied to RISK-01 (overselling).';
COMMENT ON COLUMN inventory_reservations.order_reference IS 'External order identifier. No physical FK to order-service (separate database, same rationale as ADR-002) — order-service does not exist as a caller yet.';
