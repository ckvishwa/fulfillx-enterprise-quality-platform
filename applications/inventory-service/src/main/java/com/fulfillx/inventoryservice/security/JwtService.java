package com.fulfillx.inventoryservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Validates HS256 JWTs issued by auth-service. inventory-service never
 * issues tokens of its own — it only verifies signature, expiry, and reads
 * claims off tokens auth-service already issued, entirely offline (no
 * network call to auth-service per request). See ADR-002's identity
 * pattern, applied here for the first time by a service other than
 * auth-service.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws io.jsonwebtoken.JwtException if the token is malformed,
     *         tampered (bad signature), or expired. Callers must not expose
     *         the specific exception type to API responses — see
     *         {@code JwtAuthenticationFilter}.
     */
    public Claims validateAndParse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static String roleClaim(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }
}
