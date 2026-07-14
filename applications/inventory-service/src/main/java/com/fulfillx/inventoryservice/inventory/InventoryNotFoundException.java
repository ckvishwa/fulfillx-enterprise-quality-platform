package com.fulfillx.inventoryservice.inventory;

import java.util.UUID;

/**
 * Should not occur in practice: {@code inventory_items} rows are created
 * atomically alongside their product (see {@code ProductService}), so any
 * existing product always has a matching inventory row. Kept as a defensive
 * error path rather than an assumption.
 */
public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(UUID productId) {
        super("No inventory record found for product " + productId);
    }
}
