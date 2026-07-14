package com.fulfillx.inventoryservice.reservation;

import com.fulfillx.inventoryservice.inventory.InvalidQuantityException;
import com.fulfillx.inventoryservice.reservation.api.CreateReservationRequest;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Orchestrates reservation creation's idempotency contract on top of
 * {@link ReservationWriter}'s atomic transactional operations:
 * <ul>
 *   <li>same idempotency key + same request data (order reference, product,
 *       quantity) &rarr; return the original reservation, no new side
 *       effect ({@code newlyCreated=false}).</li>
 *   <li>same idempotency key + different request data &rarr;
 *       {@link IdempotencyConflictException} ({@code IDEMPOTENCY_CONFLICT}),
 *       since silently reusing or silently creating a second reservation
 *       would both be wrong.</li>
 *   <li>unseen idempotency key &rarr; perform the atomic reservation. If a
 *       concurrent request against the very same key wins the race first,
 *       this call's insert fails on the unique constraint; the loser then
 *       re-reads and applies the same match-or-conflict check against the
 *       winner's row rather than erroring out.</li>
 * </ul>
 * This class itself is not {@code @Transactional} — each attempt's actual
 * database work happens inside {@link ReservationWriter}'s own transaction
 * boundary, so a race-losing attempt's partial writes are fully rolled back
 * before this class ever re-reads the winner's row.
 */
@Service
public class ReservationService {

    private final InventoryReservationRepository reservationRepository;
    private final ReservationWriter reservationWriter;

    public ReservationService(InventoryReservationRepository reservationRepository, ReservationWriter reservationWriter) {
        this.reservationRepository = reservationRepository;
        this.reservationWriter = reservationWriter;
    }

    public ReservationOutcome createReservation(CreateReservationRequest request, String correlationId) {
        Long quantity = request.quantity();
        if (quantity == null || quantity <= 0) {
            throw new InvalidQuantityException("quantity must be a positive number, got " + quantity);
        }

        Optional<InventoryReservation> existing = reservationRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return new ReservationOutcome(matchOrConflict(existing.get(), request), false);
        }

        try {
            InventoryReservation created = reservationWriter.reserve(
                    request.orderReference(), request.productId(), quantity, request.idempotencyKey(), correlationId);
            return new ReservationOutcome(created, true);
        } catch (DataIntegrityViolationException lostIdempotencyKeyRace) {
            InventoryReservation winner = reservationRepository.findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> lostIdempotencyKeyRace);
            return new ReservationOutcome(matchOrConflict(winner, request), false);
        }
    }

    public InventoryReservation releaseReservation(UUID reservationId) {
        return reservationWriter.release(reservationId);
    }

    private InventoryReservation matchOrConflict(InventoryReservation existing, CreateReservationRequest request) {
        boolean matches = existing.getOrderReference().equals(request.orderReference())
                && existing.getProductId().equals(request.productId())
                && existing.getQuantity() == request.quantity()
                && Objects.equals(existing.getIdempotencyKey(), request.idempotencyKey());
        if (!matches) {
            throw new IdempotencyConflictException(request.idempotencyKey());
        }
        return existing;
    }
}
