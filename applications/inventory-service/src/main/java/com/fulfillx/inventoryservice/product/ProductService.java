package com.fulfillx.inventoryservice.product;

import com.fulfillx.inventoryservice.inventory.InventoryItem;
import com.fulfillx.inventoryservice.inventory.InventoryItemRepository;
import com.fulfillx.inventoryservice.product.api.CreateProductRequest;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");

    private final ProductRepository productRepository;
    private final InventoryItemRepository inventoryItemRepository;

    public ProductService(ProductRepository productRepository, InventoryItemRepository inventoryItemRepository) {
        this.productRepository = productRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    /**
     * Creates the product and its zero-quantity inventory row together, in
     * one transaction, so every existing product is guaranteed to have a
     * matching {@code inventory_items} row — stock adjustment and
     * reservation never need to upsert one.
     */
    @Transactional
    public Product createProduct(CreateProductRequest request) {
        String normalizedSku = normalizeSku(request.sku());
        String normalizedCurrency = normalizeCurrency(request.currency());

        if (!CURRENCY_PATTERN.matcher(normalizedCurrency).matches()) {
            throw new InvalidProductRequestException(
                    "currency must be exactly three letters, got '" + request.currency() + "'");
        }
        if (productRepository.existsBySku(normalizedSku)) {
            throw new SkuAlreadyExistsException(normalizedSku);
        }

        Product product = new Product(
                normalizedSku, request.name().trim(), request.description(), request.priceMinor(), normalizedCurrency);
        product = productRepository.save(product);

        inventoryItemRepository.save(new InventoryItem(product.getId()));

        return product;
    }

    @Transactional(readOnly = true)
    public Product getProduct(UUID productId) {
        return productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Transactional(readOnly = true)
    public List<Product> listProducts() {
        return productRepository.findAll();
    }

    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase();
    }

    private static String normalizeCurrency(String currency) {
        return currency.trim().toUpperCase();
    }
}
