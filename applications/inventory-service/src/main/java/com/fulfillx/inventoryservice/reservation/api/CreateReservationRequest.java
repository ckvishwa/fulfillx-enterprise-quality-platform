package com.fulfillx.inventoryservice.reservation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * {@code correlationId} is deliberately not part of this payload — it comes
 * from the request's {@code X-Correlation-Id} header/MDC (see
 * {@code CorrelationIdSupport}), the same convention used platform-wide, not
 * something a caller sets per business object.
 */
public record CreateReservationRequest(
        @NotNull UUID orderReference,
        @NotNull UUID productId,
        @NotNull Long quantity,
        @NotBlank String idempotencyKey) {
}
