package com.fulfillx.inventoryservice.reservation.api;

import com.fulfillx.inventoryservice.reservation.InventoryReservation;
import com.fulfillx.inventoryservice.reservation.ReservationStatus;
import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID orderReference,
        UUID productId,
        long quantity,
        ReservationStatus status,
        String idempotencyKey,
        String correlationId,
        Instant createdAt,
        Instant updatedAt) {

    public static ReservationResponse from(InventoryReservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getOrderReference(),
                reservation.getProductId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getIdempotencyKey(),
                reservation.getCorrelationId(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }
}
