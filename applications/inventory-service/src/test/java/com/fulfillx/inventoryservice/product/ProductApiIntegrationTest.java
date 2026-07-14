package com.fulfillx.inventoryservice.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fulfillx.inventoryservice.support.ProductFixtures;
import com.fulfillx.inventoryservice.support.TestTokens;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end through the real Spring Security filter chain and a real
 * PostgreSQL instance (Testcontainers, version aligned with
 * docker-compose.yml's postgres:18-alpine — never H2 as the only proof of
 * Postgres behavior).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Test
    void shouldAllowAdminToCreateValidProduct() throws Exception {
        String sku = ProductFixtures.uniqueSku();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + TestTokens.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sku", sku, "name", "Widget", "priceMinor", 1999, "currency", "USD"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(sku.toUpperCase()))
                .andExpect(jsonPath("$.name").value("Widget"))
                .andExpect(jsonPath("$.priceMinor").value(1999))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldNormalizeLowercaseCurrencyAndSku() throws Exception {
        String sku = ProductFixtures.uniqueSku();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + TestTokens.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sku", sku.toLowerCase(), "name", "Widget", "priceMinor", 500, "currency", "usd"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(sku.toUpperCase()))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void shouldRejectProductCreationByCustomer() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + TestTokens.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sku", ProductFixtures.uniqueSku(), "name", "Widget", "priceMinor", 500, "currency", "USD"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldRejectProductCreationWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sku", ProductFixtures.uniqueSku(), "name", "Widget", "priceMinor", 500, "currency", "USD"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void shouldRejectDuplicateNormalizedSku() throws Exception {
        String sku = ProductFixtures.uniqueSku();
        ProductFixtures.createProduct(mockMvc, objectMapper, sku, "USD", 500);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + TestTokens.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sku", sku.toLowerCase(), "name", "Widget 2", "priceMinor", 700, "currency", "USD"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_ALREADY_EXISTS"));
    }

    @Test
    void shouldRejectInvalidNegativePrice() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + TestTokens.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sku", ProductFixtures.uniqueSku(), "name", "Widget", "priceMinor", -1, "currency", "USD"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectInvalidCurrencyFormat() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + TestTokens.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sku", ProductFixtures.uniqueSku(), "name", "Widget", "priceMinor", 500, "currency", "US"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnProductById() throws Exception {
        String sku = ProductFixtures.uniqueSku();
        String productId = ProductFixtures.createProduct(mockMvc, objectMapper, sku, "USD", 500);

        mockMvc.perform(get("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + TestTokens.customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(sku.toUpperCase()));
    }

    @Test
    void shouldReturnNotFoundForUnknownProduct() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + TestTokens.customerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void shouldRejectProductReadWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

}
