package com.fulfillx.inventoryservice.reservation;

import java.util.UUID;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(UUID reservationId) {
        super("No reservation found with id " + reservationId);
    }
}
