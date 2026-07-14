package com.fulfillx.inventoryservice.reservation.api;

import com.fulfillx.inventoryservice.reservation.ReservationOutcome;
import com.fulfillx.inventoryservice.reservation.ReservationService;
import com.fulfillx.inventoryservice.web.CorrelationIdSupport;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reservation creation and release are treated as "any authenticated
 * caller" rather than a specific internal-service identity — there is no
 * service-to-service authentication mechanism yet (order-service has no
 * caller-side endpoint of its own in this phase). This is a documented,
 * temporary limitation; see CLAUDE.md's Known Limitations and
 * docs/architecture/trust-boundaries.md.
 */
@RestController
@RequestMapping("/api/v1/inventory/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(@Valid @RequestBody CreateReservationRequest request) {
        ReservationOutcome outcome = reservationService.createReservation(request, CorrelationIdSupport.current());
        ReservationResponse body = ReservationResponse.from(outcome.reservation());

        if (outcome.newlyCreated()) {
            return ResponseEntity.created(URI.create("/api/v1/inventory/reservations/" + outcome.reservation().getId()))
                    .body(body);
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{reservationId}/release")
    public ReservationResponse release(@PathVariable UUID reservationId) {
        return ReservationResponse.from(reservationService.releaseReservation(reservationId));
    }
}
