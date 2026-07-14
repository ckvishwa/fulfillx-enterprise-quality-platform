package com.fulfillx.inventoryservice.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * Builds JWTs shaped like the ones auth-service issues, signed with the
 * fixed test secret configured in {@code application-test.yml}. Shared
 * across every API integration test in this module rather than
 * copy-pasted, since every one of them needs an ADMIN and/or CUSTOMER
 * token to exercise authorization rules.
 */
public final class TestTokens {

    public static final String TEST_JWT_SECRET =
            "test-only-secret-never-used-outside-this-test-suite-32bytes-min";

    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    private TestTokens() {
    }

    public static String adminToken() {
        return tokenFor("ADMIN");
    }

    public static String customerToken() {
        return tokenFor("CUSTOMER");
    }

    public static String tokenFor(String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(1800)))
                .signWith(SIGNING_KEY)
                .compact();
    }

    public static String expiredToken(String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", role)
                .issuedAt(Date.from(now.minusSeconds(7200)))
                .expiration(Date.from(now.minusSeconds(1)))
                .signWith(SIGNING_KEY)
                .compact();
    }
}
