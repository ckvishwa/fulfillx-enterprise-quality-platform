package com.fulfillx.inventoryservice.inventory.api;

import com.fulfillx.inventoryservice.inventory.InventoryItem;
import java.time.Instant;
import java.util.UUID;

public record InventoryResponse(
        UUID productId, long availableQuantity, long reservedQuantity, Instant updatedAt) {

    public static InventoryResponse from(InventoryItem item) {
        return new InventoryResponse(
                item.getProductId(), item.getAvailableQuantity(), item.getReservedQuantity(), item.getUpdatedAt());
    }
}
