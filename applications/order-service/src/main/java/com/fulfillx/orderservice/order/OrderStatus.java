package com.fulfillx.orderservice.order;

/**
 * The full order lifecycle, including compensation and refund paths.
 *
 * See docs/architecture/order-lifecycle.md for the documented, legal state
 * transitions. This enum intentionally defines the full state space now;
 * transition-guard logic (rejecting illegal moves such as
 * {@code DELIVERED -> CREATED}) is domain behavior introduced in Phase 2 and
 * is not implemented here yet.
 */
public enum OrderStatus {
    CREATED,
    INVENTORY_RESERVED,
    INVENTORY_REJECTED,
    PAYMENT_AUTHORIZED,
    PAYMENT_FAILED,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    INVENTORY_RELEASED,
    REFUND_PENDING,
    REFUNDED
}
