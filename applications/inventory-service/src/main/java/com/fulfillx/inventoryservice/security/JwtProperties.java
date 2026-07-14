package com.fulfillx.inventoryservice.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bound from {@code auth.jwt.secret}, backed by the {@code AUTH_JWT_SECRET}
 * environment variable — the same signing secret auth-service uses to
 * issue tokens, so inventory-service can verify them locally (signature +
 * expiry) without calling back to auth-service. There is no hard-coded
 * default — startup fails fast if one isn't supplied, matching
 * auth-service's own fail-fast contract. inventory-service never issues
 * tokens itself, so unlike auth-service's JwtProperties there is no
 * expiration-minutes setting here.
 */
@Validated
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {

    @NotBlank
    private String secret;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
