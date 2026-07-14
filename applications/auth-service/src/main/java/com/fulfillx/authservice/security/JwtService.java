package com.fulfillx.authservice.security;

import com.fulfillx.authservice.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and validates HS256 JWTs. The signing key is derived once from the
 * configured secret (never generated randomly at startup) so tokens remain
 * valid across restarts of this service.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(properties.getExpirationMinutes()));

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_EMAIL, user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public long expirationSeconds() {
        return Duration.ofMinutes(properties.getExpirationMinutes()).toSeconds();
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
