package com.fulfillx.inventoryservice.inventory.api;

import com.fulfillx.inventoryservice.inventory.InventoryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST .../adjust} was chosen over PUT deliberately: an adjustment is
 * a delta applied on top of current state, not an idempotent full-resource
 * replacement (replaying the same POST twice produces a different result
 * each time, which is exactly what PUT's idempotency contract rules out).
 * See docs/decisions/ADR-003-inventory-consistency-and-atomic-reservation.md.
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/{productId}/adjust")
    public InventoryResponse adjustStock(@PathVariable UUID productId, @Valid @RequestBody AdjustStockRequest request) {
        return InventoryResponse.from(inventoryService.adjustStock(productId, request.quantity()));
    }

    @GetMapping("/{productId}")
    public InventoryResponse getInventory(@PathVariable UUID productId) {
        return InventoryResponse.from(inventoryService.getInventory(productId));
    }
}
