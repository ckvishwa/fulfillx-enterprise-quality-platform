package com.fulfillx.inventoryservice.product;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID productId) {
        super("No product found with id " + productId);
    }
}
