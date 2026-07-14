package com.fulfillx.authservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fulfillx.authservice.user.User;
import com.fulfillx.authservice.user.UserRole;
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
 * Pure unit tests — no Spring context, no database. Expired and tampered
 * tokens are crafted directly rather than waited for or fuzzed, so these
 * tests are deterministic and instantaneous (no sleeps, per the project's
 * testing standards).
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-not-used-anywhere-else-32-bytes-min";

    private final JwtProperties properties = properties(SECRET, 30);
    private final JwtService jwtService = new JwtService(properties);

    @Test
    void shouldIssueTokenThatValidatesBackToTheSameSubjectAndRole() throws Exception {
        User user = withGeneratedId(new User("customer@example.com", "irrelevant-hash", UserRole.CUSTOMER));

        String token = jwtService.issue(user);
        var claims = jwtService.validateAndParse(token);

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(JwtService.roleClaim(claims)).isEqualTo("CUSTOMER");
        assertThat(claims.getExpiration()).isAfter(new Date());
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
    void shouldRejectTamperedToken() {
        String validToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "CUSTOMER")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(keyFor(SECRET))
                .compact();

        String[] parts = validToken.split("\\.");
        // Flip a character in the signature segment.
        char[] signatureChars = parts[2].toCharArray();
        signatureChars[0] = signatureChars[0] == 'A' ? 'B' : 'A';
        String tamperedToken = parts[0] + "." + parts[1] + "." + new String(signatureChars);

        assertThatThrownBy(() -> jwtService.validateAndParse(tamperedToken))
                .isInstanceOf(SignatureException.class);
    }

    private static SecretKey keyFor(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static JwtProperties properties(String secret, long expirationMinutes) {
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        props.setExpirationMinutes(expirationMinutes);
        return props;
    }

    /** {@code id} is normally assigned by Hibernate on persist; this test never touches the database, so it's set via reflection instead. */
    private static User withGeneratedId(User user) throws Exception {
        var idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, UUID.randomUUID());
        return user;
    }
}
