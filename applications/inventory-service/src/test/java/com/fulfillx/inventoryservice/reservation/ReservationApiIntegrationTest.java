package com.fulfillx.inventoryservice.reservation;

import static org.assertj.core.api.Assertions.assertThat;
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

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    private String productWithStock(long quantity) throws Exception {
        String productId = ProductFixtures.createProduct(mockMvc, objectMapper, ProductFixtures.uniqueSku(), "USD", 500);
        mockMvc.perform(post("/api/v1/inventory/" + productId + "/adjust")
                        .header("Authorization", "Bearer " + TestTokens.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", quantity))))
                .andExpect(status().isOk());
        return productId;
    }

    private String reserveRequestBody(UUID orderReference, String productId, long quantity, String idempotencyKey)
            throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "orderReference", orderReference.toString(),
                "productId", productId,
                "quantity", quantity,
                "idempotencyKey", idempotencyKey));
    }

    @Test
    void shouldReserveSuccessfullyAndUpdateInventoryAtomically() throws Exception {
        String productId = productWithStock(10);

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("Authorization", "Bearer " + TestTokens.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveRequestBody(UUID.randomUUID(), productId, 4, "key-" + UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.quantity").value(4));

        mockMvc.perform(get("/api/v1/inventory/" + productId)
                        .header("Authorization", "Bearer " + TestTokens.customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(6))
                .andExpect(jsonPath("$.reservedQuantity").value(4));
    }

    @Test
    void shouldRejectZeroQuantityReservation() throws Exception {
        String productId = productWithStock(10);

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("Authorization", "Bearer " + TestTokens.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveRequestBody(UUID.randomUUID(), productId, 0, "key-" + UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
    }

    @Test
    void shouldRejectNegativeQuantityReservation() throws Exception {
        String productId = productWithStock(10);

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("Authorization", "Bearer " + TestTokens.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveRequestBody(UUID.randomUUID(), productId, -2, "key-" + UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
    }

    @Test
    void shouldReturnInsufficientInventoryWhenStockTooLow() throws Exception {
        String productId = productWithStock(2);

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("Authorization", "Bearer " + TestTokens.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveRequestBody(UUID.randomUUID(), productId, 5, "key-" + UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));

        // A failed reservation attempt must leave inventory untouched.
        mockMvc.perform(get("/api/v1/inventory/" + productId)
                        .header("Authorization", "Bearer " + TestTokens.customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(2))
                .andExpect(jsonPath("$.reservedQuantity").value(0));
    }

    @Test
    void shouldRejectReservationForUnknownProduct() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("Authorization", "Bearer " + TestTokens.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveRequestBody(UUID.randomUUID(), UUID.randomUUID().toString(), 1, "key-" + UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void shouldRejectReservationWithoutAuthentication() throws Exception {
        String productId = productWithStock(10);

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveRequestBody(UUID.randomUUID(), productId, 1, "key-" + UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldTreatRepeatedIdempotencyKeyAsSameReservationWithNoAdditionalSideEffect() throws Exception {
        String productId = productWithStock(10);
        UUID orderReference = UUID.randomUUID();
        String idempotencyKey = "key-" + UUID.randomUUID();

        String firstResponse = mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("Authorization", "Bearer " + TestTokens.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveRequestBody(orderReference, productId, 3, idempotencyKey)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reservationId = objectMapper.readTree(firstResponse).get("id").asText();

        String replayResponse = mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("Authorization", "Bearer " + TestTokens.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveRequestBody(orderReference, productId, 3, idempotencyKey)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(replayResponse).get("id").asText()).isEqualTo(reservationId);

        // Only one reservation's worth of stock was ever taken.
        mockMvc.perform(get("/api/v1/inventory/" + productId)
                        .header("Authorization", "Bearer " + TestTokens.customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(7))
                .andExpect(jsonPath("$.reservedQuantity").value(3));
    }

    @Test
    void shouldRejectConflictingReuseOfIdempotencyKey() throws Exception {
        String productId = productWithStock(10);
        String idempotencyKey = "key-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("Authorization", "Bearer " + TestTokens.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveRequestBody(UUID.randomUUID(), productId, 2, idempotencyKey)))
                .andExpect(status().isCreated());

        // Same key, different quantity -> a client bug, not a safe replay.
        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("Authorization", "Bearer " + TestTokens.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveRequestBody(UUID.randomUUID(), productId, 5, idempotencyKey)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void shouldReleaseReservationAndRestoreInventoryExactlyOnce() throws Exception {
        String productId = productWithStock(10);

        String createResponse = mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("Authorization", "Bearer " + TestTokens.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveRequestBody(UUID.randomUUID(), productId, 4, "key-" + UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reservationId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(post("/api/v1/inventory/reservations/" + reservationId + "/release")
                        .header("Authorization", "Bearer " + TestTokens.customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        mockMvc.perform(get("/api/v1/inventory/" + productId)
                        .header("Authorization", "Bearer " + TestTokens.customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(10))
                .andExpect(jsonPath("$.reservedQuantity").value(0));
    }

    @Test
    void shouldTreatRepeatedReleaseAsIdempotentNoOp() throws Exception {
        String productId = productWithStock(10);

        String createResponse = mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("Authorization", "Bearer " + TestTokens.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveRequestBody(UUID.randomUUID(), productId, 4, "key-" + UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reservationId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(post("/api/v1/inventory/reservations/" + reservationId + "/release")
                        .header("Authorization", "Bearer " + TestTokens.customerToken()))
                .andExpect(status().isOk());

        // Second release of the same reservation must not restore stock again.
        mockMvc.perform(post("/api/v1/inventory/reservations/" + reservationId + "/release")
                        .header("Authorization", "Bearer " + TestTokens.customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        mockMvc.perform(get("/api/v1/inventory/" + productId)
                        .header("Authorization", "Bearer " + TestTokens.customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(10))
                .andExpect(jsonPath("$.reservedQuantity").value(0));
    }

    @Test
    void shouldRejectReleaseOfUnknownReservation() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/reservations/" + UUID.randomUUID() + "/release")
                        .header("Authorization", "Bearer " + TestTokens.customerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void shouldRejectReleaseWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/reservations/" + UUID.randomUUID() + "/release"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
}
