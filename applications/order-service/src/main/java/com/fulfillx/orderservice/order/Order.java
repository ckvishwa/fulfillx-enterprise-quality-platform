package com.fulfillx.orderservice.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Minimal order persistence model for Phase 0/1.
 *
 * Deliberately excludes line items, shipment detail, and payment/reservation
 * linkage — those arrive with the Phase 2 vertical slice. Money is stored as
 * integer minor units (cents) per docs/architecture/data-ownership.md; never
 * floating point.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "subtotal_minor", nullable = false)
    private long subtotalMinor;

    @Column(name = "tax_minor", nullable = false)
    private long taxMinor;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
        // JPA
    }

    public Order(UUID customerId, String idempotencyKey, OrderStatus status, String currency,
            long subtotalMinor, long taxMinor, long totalMinor, UUID correlationId) {
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.currency = currency;
        this.subtotalMinor = subtotalMinor;
        this.taxMinor = taxMinor;
        this.totalMinor = totalMinor;
        this.correlationId = correlationId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public long getSubtotalMinor() {
        return subtotalMinor;
    }

    public long getTaxMinor() {
        return taxMinor;
    }

    public long getTotalMinor() {
        return totalMinor;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
