package com.fulfillx.inventoryservice.product.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * {@code sku} and {@code currency} are normalized (trimmed + uppercased) by
 * {@code ProductService} before validation of their format — a lowercase or
 * mixed-case currency is normalized, not rejected outright, matching the
 * SKU/email normalization precedent elsewhere in this platform. A currency
 * that still isn't exactly three letters after normalization is rejected.
 */
public record CreateProductRequest(
        @NotBlank String sku,
        @NotBlank String name,
        String description,
        @PositiveOrZero long priceMinor,
        @NotBlank String currency) {
}
