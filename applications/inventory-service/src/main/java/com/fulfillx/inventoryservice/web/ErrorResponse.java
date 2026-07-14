package com.fulfillx.inventoryservice.web;

import java.time.Instant;
import java.util.List;

/**
 * The platform-wide error contract (see ADR-001 / auth-service's
 * ErrorResponse): stable, machine-readable, correlated, free of stack
 * traces and internal details.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String correlationId,
        List<String> details) {

    public static ErrorResponse of(int status, String code, String message, List<String> details) {
        return new ErrorResponse(Instant.now(), status, code, message, CorrelationIdSupport.current(), details);
    }

    public static ErrorResponse of(int status, String code, String message) {
        return of(status, code, message, List.of());
    }
}
