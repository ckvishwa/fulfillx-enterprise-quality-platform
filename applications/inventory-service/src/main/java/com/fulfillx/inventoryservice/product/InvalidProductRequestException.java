package com.fulfillx.inventoryservice.product;

/**
 * Raised for product-field validation that can only be checked after
 * normalization (e.g. currency format) and therefore can't be expressed as
 * a plain Bean Validation annotation on the request DTO. Mapped to the same
 * {@code VALIDATION_ERROR} contract as {@code MethodArgumentNotValidException}.
 */
public class InvalidProductRequestException extends RuntimeException {

    public InvalidProductRequestException(String message) {
        super(message);
    }
}
