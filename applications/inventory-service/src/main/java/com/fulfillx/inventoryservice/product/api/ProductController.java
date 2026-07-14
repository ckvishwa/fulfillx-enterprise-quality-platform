package com.fulfillx.inventoryservice.product.api;

import com.fulfillx.inventoryservice.product.Product;
import com.fulfillx.inventoryservice.product.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        Product product = productService.createProduct(request);
        ProductResponse body = ProductResponse.from(product);
        return ResponseEntity.created(URI.create("/api/v1/products/" + product.getId())).body(body);
    }

    @GetMapping("/{productId}")
    public ProductResponse getProduct(@PathVariable UUID productId) {
        return ProductResponse.from(productService.getProduct(productId));
    }

    @GetMapping
    public List<ProductResponse> listProducts() {
        return productService.listProducts().stream().map(ProductResponse::from).toList();
    }
}
