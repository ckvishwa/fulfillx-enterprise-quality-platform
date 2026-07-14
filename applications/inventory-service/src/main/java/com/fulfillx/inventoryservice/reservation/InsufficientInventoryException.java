package com.fulfillx.inventoryservice.reservation;

import java.util.UUID;

public class InsufficientInventoryException extends RuntimeException {

    public InsufficientInventoryException(UUID productId, long requestedQuantity) {
        super("Insufficient available inventory for product " + productId + " to reserve quantity " + requestedQuantity);
    }
}
