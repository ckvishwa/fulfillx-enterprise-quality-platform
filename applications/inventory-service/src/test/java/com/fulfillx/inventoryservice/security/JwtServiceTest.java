package com.fulfillx.inventoryservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests — no Spring context, no database. inventory-service only
 * validates tokens (it never issues them), so unlike auth-service's
 * JwtServiceTest there is no "issue" case; tokens here are crafted directly
 * with the same secret, mirroring what auth-service would actually issue.
 * Expired and tampered tokens are crafted directly rather than waited for
 * or fuzzed, so these tests are deterministic and instantaneous (no
 * sleeps, per the project's testing standards).
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-not-used-anywhere-else-32-bytes-min";

    private final JwtService jwtService = new JwtService(properties(SECRET));

    @Test
    void shouldValidateTokenSignedWithTheConfiguredSecret() {
        UUID userId = UUID.randomUUID();
        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(1800)))
                .signWith(keyFor(SECRET))
                .compact();

        var claims = jwtService.validateAndParse(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(JwtService.roleClaim(claims)).isEqualTo("ADMIN");
    }

    @Test
    void shouldRejectExpiredToken() {
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "CUSTOMER")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(1))) // already expired
                .signWith(keyFor(SECRET))
                .compact();

        assertThatThrownBy(() -> jwtService.validateAndParse(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void shouldRejectTokenSignedWithADifferentSecret() {
        String tokenFromAnotherSigner = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "CUSTOMER")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(keyFor("a-completely-different-secret-also-32-bytes-min"))
                .compact();

        assertThatThrownBy(() -> jwtService.validateAndParse(tokenFromAnotherSigner))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void shouldRejectTamperedToken() {
        String validToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "CUSTOMER")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(keyFor(SECRET))
                .compact();

        String[] parts = validToken.split("\\.");
        char[] signatureChars = parts[2].toCharArray();
        signatureChars[0] = signatureChars[0] == 'A' ? 'B' : 'A';
        String tamperedToken = parts[0] + "." + parts[1] + "." + new String(signatureChars);

        assertThatThrownBy(() -> jwtService.validateAndParse(tamperedToken))
                .isInstanceOf(SignatureException.class);
    }

    private static SecretKey keyFor(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static JwtProperties properties(String secret) {
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        return props;
    }
}
