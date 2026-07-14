package com.fulfillx.inventoryservice.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared across every test class that needs a product to exist before it
 * can exercise inventory/reservation behavior (Product, Inventory,
 * Reservation, and Concurrency integration tests) — real, demonstrated
 * duplication, not a speculative abstraction.
 */
public final class ProductFixtures {

    private ProductFixtures() {
    }

    public static String uniqueSku() {
        return "sku-" + UUID.randomUUID();
    }

    public static String createProduct(MockMvc mockMvc, JsonMapper objectMapper, String sku, String currency, long priceMinor)
            throws Exception {
        String response = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + TestTokens.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sku", sku, "name", "Widget", "priceMinor", priceMinor, "currency", currency))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }
}
