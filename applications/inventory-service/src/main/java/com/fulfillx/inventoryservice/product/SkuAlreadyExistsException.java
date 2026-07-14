package com.fulfillx.inventoryservice.product;

public class SkuAlreadyExistsException extends RuntimeException {

    public SkuAlreadyExistsException(String sku) {
        super("A product with SKU '" + sku + "' already exists");
    }
}
