package com.fulfillx.inventoryservice.reservation;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {

    Optional<InventoryReservation> findByIdempotencyKey(String idempotencyKey);

    /**
     * Atomic conditional transition RESERVED -> RELEASED, guarded so a
     * concurrent or repeated release request can only ever win this race
     * once. Returns rows updated (0 or 1); 0 means "already released" (safe
     * to treat as an idempotent no-op) once existence has been confirmed by
     * the caller.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE InventoryReservation r
            SET r.status = com.fulfillx.inventoryservice.reservation.ReservationStatus.RELEASED,
                r.version = r.version + 1,
                r.updatedAt = CURRENT_TIMESTAMP
            WHERE r.id = :id
              AND r.status = com.fulfillx.inventoryservice.reservation.ReservationStatus.RESERVED
            """)
    int markReleasedAtomically(@Param("id") UUID id);
}
