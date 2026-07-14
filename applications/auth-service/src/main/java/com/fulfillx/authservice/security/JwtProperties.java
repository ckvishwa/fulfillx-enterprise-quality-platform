package com.fulfillx.authservice.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bound from {@code auth.jwt.*}, backed by the {@code AUTH_JWT_SECRET} /
 * {@code AUTH_JWT_EXPIRATION_MINUTES} environment variables (see
 * application.yml). There is no hard-coded default secret — startup fails
 * fast if one isn't supplied, so a real environment can never silently run
 * on a well-known placeholder.
 */
@Validated
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {

    @NotBlank
    private String secret;

    @Min(1)
    private long expirationMinutes = 30;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }
}
