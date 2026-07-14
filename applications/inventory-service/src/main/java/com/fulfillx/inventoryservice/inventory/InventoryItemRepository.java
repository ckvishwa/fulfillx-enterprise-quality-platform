package com.fulfillx.inventoryservice.inventory;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    Optional<InventoryItem> findByProductId(UUID productId);

    /**
     * Atomic conditional reservation: decrements available and increments
     * reserved in a single statement guarded by
     * {@code available_quantity >= :quantity}. PostgreSQL's row-level
     * locking under this UPDATE is what makes concurrent reservation
     * attempts against the same product safe — there is no
     * read-then-check-then-write in application code. Returns the number
     * of rows updated (0 or 1); the caller must treat 0 as "insufficient
     * inventory," not retry blindly. See
     * docs/decisions/ADR-003-inventory-consistency-and-atomic-reservation.md.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE InventoryItem i
            SET i.availableQuantity = i.availableQuantity - :quantity,
                i.reservedQuantity = i.reservedQuantity + :quantity,
                i.version = i.version + 1,
                i.updatedAt = CURRENT_TIMESTAMP
            WHERE i.productId = :productId
              AND i.availableQuantity >= :quantity
            """)
    int reserveAtomically(@Param("productId") UUID productId, @Param("quantity") long quantity);

    /**
     * Atomic conditional release: restores available and decrements
     * reserved, guarded by {@code reserved_quantity >= :quantity} so a
     * release can never push reserved_quantity negative even under a
     * concurrent double-release attempt.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE InventoryItem i
            SET i.availableQuantity = i.availableQuantity + :quantity,
                i.reservedQuantity = i.reservedQuantity - :quantity,
                i.version = i.version + 1,
                i.updatedAt = CURRENT_TIMESTAMP
            WHERE i.productId = :productId
              AND i.reservedQuantity >= :quantity
            """)
    int releaseAtomically(@Param("productId") UUID productId, @Param("quantity") long quantity);

    /**
     * Atomic conditional stock adjustment (increase only — see
     * {@code InventoryService}): guarded by a nonnegative-delta check at
     * the service layer, this simply adds the delta atomically.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE InventoryItem i
            SET i.availableQuantity = i.availableQuantity + :delta,
                i.version = i.version + 1,
                i.updatedAt = CURRENT_TIMESTAMP
            WHERE i.productId = :productId
              AND i.availableQuantity + :delta >= 0
            """)
    int adjustAtomically(@Param("productId") UUID productId, @Param("delta") long delta);
}
