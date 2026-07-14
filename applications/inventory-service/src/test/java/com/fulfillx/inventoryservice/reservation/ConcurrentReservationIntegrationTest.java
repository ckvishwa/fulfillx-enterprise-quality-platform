package com.fulfillx.inventoryservice.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fulfillx.inventoryservice.support.ProductFixtures;
import com.fulfillx.inventoryservice.support.TestTokens;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * Deterministically proves RISK-01 (inventory overselling under concurrent
 * orders): 20 concurrent attempts to reserve 1 unit each against a product
 * stocked with exactly 5 must yield exactly 5 successes and 15 controlled
 * INSUFFICIENT_INVENTORY failures — never more than 5 successes (which
 * would mean overselling) and never fewer (which would mean the atomic
 * update rejected a request it should have allowed). All 20 requests start
 * together via a {@link CyclicBarrier} rather than relying on incidental
 * thread scheduling, and every wait is bounded — no fixed sleeps.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrentReservationIntegrationTest {

    private static final int CONCURRENT_ATTEMPTS = 20;
    private static final long INITIAL_STOCK = 5;
    private static final long EXPECTED_SUCCESSES = 5;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Test
    void exactlyStockCountReservationsSucceedUnderConcurrentAttempts() throws Exception {
        String productId = ProductFixtures.createProduct(mockMvc, objectMapper, ProductFixtures.uniqueSku(), "USD", 100);
        mockMvc.perform(post("/api/v1/inventory/" + productId + "/adjust")
                .header("Authorization", "Bearer " + TestTokens.adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("quantity", INITIAL_STOCK))));

        CyclicBarrier startBarrier = new CyclicBarrier(CONCURRENT_ATTEMPTS);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS);
        try {
            List<Callable<Integer>> attempts = java.util.stream.IntStream.range(0, CONCURRENT_ATTEMPTS)
                    .<Callable<Integer>>mapToObj(i -> () -> {
                        startBarrier.await(30, TimeUnit.SECONDS);
                        String body = objectMapper.writeValueAsString(Map.of(
                                "orderReference", UUID.randomUUID().toString(),
                                "productId", productId,
                                "quantity", 1,
                                "idempotencyKey", "concurrency-key-" + UUID.randomUUID()));
                        return mockMvc.perform(post("/api/v1/inventory/reservations")
                                        .header("Authorization", "Bearer " + TestTokens.customerToken())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                                .andReturn().getResponse().getStatus();
                    })
                    .toList();

            List<Future<Integer>> futures = executor.invokeAll(attempts, 60, TimeUnit.SECONDS);

            long successCount = 0;
            long insufficientInventoryCount = 0;
            for (Future<Integer> future : futures) {
                int status = future.get(5, TimeUnit.SECONDS);
                if (status == 201) {
                    successCount++;
                } else if (status == 409) {
                    insufficientInventoryCount++;
                }
            }

            assertThat(successCount).as("successful reservations").isEqualTo(EXPECTED_SUCCESSES);
            assertThat(insufficientInventoryCount)
                    .as("controlled insufficient-inventory failures")
                    .isEqualTo(CONCURRENT_ATTEMPTS - EXPECTED_SUCCESSES);
        } finally {
            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).as("executor terminated within bound").isTrue();
        }

        mockMvc.perform(get("/api/v1/inventory/" + productId)
                        .header("Authorization", "Bearer " + TestTokens.customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(0))
                .andExpect(jsonPath("$.reservedQuantity").value(EXPECTED_SUCCESSES));

        long reservedRowCount = reservationRepository.findAll().stream()
                .filter(r -> r.getProductId().toString().equals(productId))
                .filter(r -> r.getStatus() == ReservationStatus.RESERVED)
                .count();
        assertThat(reservedRowCount).as("exactly one reservation row per successful attempt").isEqualTo(EXPECTED_SUCCESSES);
    }
}
