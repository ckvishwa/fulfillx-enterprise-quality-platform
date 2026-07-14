package com.fulfillx.inventoryservice.reservation;

/**
 * @param newlyCreated false when this call returned an existing reservation
 *                      because of an idempotent replay (matching
 *                      idempotency key) rather than performing a new
 *                      reservation — lets the controller choose 200 vs 201.
 */
public record ReservationOutcome(InventoryReservation reservation, boolean newlyCreated) {
}
