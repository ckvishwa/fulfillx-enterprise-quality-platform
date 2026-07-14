package com.fulfillx.inventoryservice.inventory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldAllowAdminToIncreaseStock() throws Exception {
        String productId = ProductFixtures.createProduct(mockMvc, objectMapper, ProductFixtures.uniqueSku(), "USD", 500);

        mockMvc.perform(post("/api/v1/inventory/" + productId + "/adjust")
                        .header("Authorization", "Bearer " + TestTokens.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(10))
                .andExpect(jsonPath("$.reservedQuantity").value(0));
    }

    @Test
    void shouldRejectNegativeStockAdjustment() throws Exception {
        String productId = ProductFixtures.createProduct(mockMvc, objectMapper, ProductFixtures.uniqueSku(), "USD", 500);

        mockMvc.perform(post("/api/v1/inventory/" + productId + "/adjust")
                        .header("Authorization", "Bearer " + TestTokens.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", -5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
    }

    @Test
    void shouldRejectZeroStockAdjustment() throws Exception {
        String productId = ProductFixtures.createProduct(mockMvc, objectMapper, ProductFixtures.uniqueSku(), "USD", 500);

        mockMvc.perform(post("/api/v1/inventory/" + productId + "/adjust")
                        .header("Authorization", "Bearer " + TestTokens.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
    }

    @Test
    void shouldRejectStockAdjustmentForUnknownProduct() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/" + UUID.randomUUID() + "/adjust")
                        .header("Authorization", "Bearer " + TestTokens.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 5))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void shouldRejectStockAdjustmentByNonAdmin() throws Exception {
        String productId = ProductFixtures.createProduct(mockMvc, objectMapper, ProductFixtures.uniqueSku(), "USD", 500);

        mockMvc.perform(post("/api/v1/inventory/" + productId + "/adjust")
                        .header("Authorization", "Bearer " + TestTokens.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 5))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldReturnInventoryForProduct() throws Exception {
        String productId = ProductFixtures.createProduct(mockMvc, objectMapper, ProductFixtures.uniqueSku(), "USD", 500);

        mockMvc.perform(get("/api/v1/inventory/" + productId)
                        .header("Authorization", "Bearer " + TestTokens.customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.availableQuantity").value(0))
                .andExpect(jsonPath("$.reservedQuantity").value(0));
    }

    @Test
    void shouldReturnInventoryNotFoundStyleErrorForUnknownProduct() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + TestTokens.customerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void databaseConstraintShouldPreventNegativeAvailableQuantity() throws Exception {
        // Not @Transactional: product creation commits via its own
        // service-layer transaction over an HTTP call. Wrapping this test
        // method in a transaction would defer that INSERT's visibility to
        // this raw JDBC connection until flush, making the UPDATE below
        // match zero rows instead of tripping the CHECK constraint.
        String productId = ProductFixtures.createProduct(mockMvc, objectMapper, ProductFixtures.uniqueSku(), "USD", 500);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE inventory_items SET available_quantity = -1 WHERE product_id = ?::uuid", productId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseConstraintShouldPreventNegativeReservedQuantity() throws Exception {
        String productId = ProductFixtures.createProduct(mockMvc, objectMapper, ProductFixtures.uniqueSku(), "USD", 500);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE inventory_items SET reserved_quantity = -1 WHERE product_id = ?::uuid", productId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
