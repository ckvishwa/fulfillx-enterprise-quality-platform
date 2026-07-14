package com.fulfillx.orderservice.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves, against a real PostgreSQL instance (not H2), that:
 *
 * <ul>
 *   <li>the Flyway V1 migration applies cleanly to an empty database, and</li>
 *   <li>the {@code orders} table's unique idempotency-key constraint actually
 *       protects against duplicate order submission at the database layer,
 *       which is the risk documented in
 *       docs/business-risks/business-risk-register.md.</li>
 * </ul>
 *
 * This is deliberately the first test in the repository: it establishes real
 * infrastructure as the baseline rather than deferring it to a later phase.
 */
@Testcontainers
@SpringBootTest
class OrderPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void shouldPersistOrderAndExposeItThroughFlywayMigratedSchema() {
        Order order = new Order(
                UUID.randomUUID(),
                "idem-" + UUID.randomUUID(),
                OrderStatus.CREATED,
                "USD",
                1_999L,
                160L,
                2_159L,
                UUID.randomUUID());

        Order saved = orderRepository.saveAndFlush(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(saved.getTotalMinor()).isEqualTo(2_159L);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();

        Order reloaded = orderRepository.findByIdempotencyKey(saved.getIdempotencyKey())
                .orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(saved.getId());
    }

    @Test
    void shouldRejectDuplicateIdempotencyKeyAtTheDatabaseLevel() {
        String sharedIdempotencyKey = "idem-" + UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Order first = new Order(
                customerId, sharedIdempotencyKey, OrderStatus.CREATED, "USD",
                1_000L, 80L, 1_080L, UUID.randomUUID());
        orderRepository.saveAndFlush(first);

        Order duplicate = new Order(
                customerId, sharedIdempotencyKey, OrderStatus.CREATED, "USD",
                1_000L, 80L, 1_080L, UUID.randomUUID());

        assertThatThrownBy(() -> orderRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
