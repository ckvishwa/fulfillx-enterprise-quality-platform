package com.fulfillx.inventoryservice.inventory;

import com.fulfillx.inventoryservice.product.ProductNotFoundException;
import com.fulfillx.inventoryservice.product.ProductRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryItemRepository inventoryItemRepository;

    public InventoryService(ProductRepository productRepository, InventoryItemRepository inventoryItemRepository) {
        this.productRepository = productRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    @Transactional
    public InventoryItem adjustStock(UUID productId, Long quantity) {
        if (quantity == null || quantity <= 0) {
            throw new InvalidQuantityException("quantity must be a positive number, got " + quantity);
        }
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }

        int rowsUpdated = inventoryItemRepository.adjustAtomically(productId, quantity);
        if (rowsUpdated != 1) {
            // Should not happen: a product's inventory row is created
            // atomically alongside the product itself (see ProductService).
            throw new InventoryNotFoundException(productId);
        }

        return inventoryItemRepository.findByProductId(productId).orElseThrow(() -> new InventoryNotFoundException(productId));
    }

    @Transactional(readOnly = true)
    public InventoryItem getInventory(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
        return inventoryItemRepository.findByProductId(productId).orElseThrow(() -> new InventoryNotFoundException(productId));
    }
}
