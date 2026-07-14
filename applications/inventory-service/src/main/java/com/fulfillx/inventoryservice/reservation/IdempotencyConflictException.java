package com.fulfillx.inventoryservice.reservation;

/**
 * Thrown when a reservation request reuses an idempotency key already tied
 * to a different order reference, product, or quantity. Same
 * idempotency-key-but-different-payload is a client bug, not a safe replay
 * — it must not be silently treated as either a duplicate or a new
 * reservation.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key '" + idempotencyKey + "' was already used with different request data");
    }
}
