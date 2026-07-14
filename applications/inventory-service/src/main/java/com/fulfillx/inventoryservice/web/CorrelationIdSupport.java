package com.fulfillx.inventoryservice.web;

import org.slf4j.MDC;

public final class CorrelationIdSupport {

    private CorrelationIdSupport() {
    }

    /** Returns the current request's correlation ID, or {@code "unknown"} if none is set (should not happen in practice — {@link CorrelationIdFilter} runs on every request). */
    public static String current() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        return value != null ? value : "unknown";
    }
}
