package com.fulfillx.inventoryservice.inventory.api;

import jakarta.validation.constraints.NotNull;

/**
 * This phase's adjustment endpoint only increases stock (e.g. restock,
 * correction upward) — {@code quantity} must be strictly positive.
 * Decreasing stock through this endpoint is out of scope; the only sanctioned
 * way available_quantity decreases is an atomic reservation. A negative or
 * zero quantity is rejected as {@code INVALID_QUANTITY} rather than silently
 * accepted, since {@code long} can't distinguish "absent" from zero the way
 * a boxed type could — validated explicitly in {@code InventoryService}
 * rather than via a Bean Validation annotation for a clearer error message.
 */
public record AdjustStockRequest(@NotNull Long quantity) {
}
