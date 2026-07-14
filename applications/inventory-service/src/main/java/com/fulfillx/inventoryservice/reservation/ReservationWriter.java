package com.fulfillx.inventoryservice.reservation;

import com.fulfillx.inventoryservice.inventory.InventoryItemRepository;
import com.fulfillx.inventoryservice.inventory.InventoryNotFoundException;
import com.fulfillx.inventoryservice.product.ProductNotFoundException;
import com.fulfillx.inventoryservice.product.ProductRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds the two atomic, transactional write operations on the reservation
 * ledger. Kept as a bean separate from {@link ReservationService} on
 * purpose: {@code ReservationService} needs to catch exceptions thrown
 * partway through a transaction (see its idempotency race handling), which
 * only works reliably across a real Spring proxy boundary — a private
 * transactional method called via {@code this} would bypass the proxy and
 * silently run without transactional semantics.
 */
@Service
public class ReservationWriter {

    private final ProductRepository productRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryReservationRepository reservationRepository;

    public ReservationWriter(
            ProductRepository productRepository,
            InventoryItemRepository inventoryItemRepository,
            InventoryReservationRepository reservationRepository) {
        this.productRepository = productRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.reservationRepository = reservationRepository;
    }

    /**
     * Atomically decrements available / increments reserved and inserts the
     * reservation row in one transaction. If two concurrent callers race on
     * the same {@code idempotencyKey}, the loser's insert fails with
     * {@link org.springframework.dao.DataIntegrityViolationException}
     * (unique constraint) and this whole transaction rolls back, including
     * the stock decrement — {@link ReservationService} is responsible for
     * then reading back the winner's row.
     */
    @Transactional
    public InventoryReservation reserve(
            UUID orderReference, UUID productId, long quantity, String idempotencyKey, String correlationId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }

        int rowsUpdated = inventoryItemRepository.reserveAtomically(productId, quantity);
        if (rowsUpdated != 1) {
            throw new InsufficientInventoryException(productId, quantity);
        }

        InventoryReservation reservation =
                new InventoryReservation(orderReference, productId, quantity, idempotencyKey, correlationId);
        return reservationRepository.save(reservation);
    }

    /**
     * Atomically transitions RESERVED -> RELEASED and restores inventory in
     * one transaction. Safe to call repeatedly: if the reservation is
     * already RELEASED (by this call or a concurrent one), it's returned
     * as-is with no further inventory change — stock is only ever restored
     * once per reservation.
     */
    @Transactional
    public InventoryReservation release(UUID reservationId) {
        InventoryReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return reservation;
        }

        int reservationRowsUpdated = reservationRepository.markReleasedAtomically(reservationId);
        if (reservationRowsUpdated != 1) {
            // Lost a race with a concurrent release between the read above
            // and this UPDATE; the winner already restored inventory.
            return reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        }

        int inventoryRowsUpdated =
                inventoryItemRepository.releaseAtomically(reservation.getProductId(), reservation.getQuantity());
        if (inventoryRowsUpdated != 1) {
            throw new InventoryNotFoundException(reservation.getProductId());
        }

        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }
}
