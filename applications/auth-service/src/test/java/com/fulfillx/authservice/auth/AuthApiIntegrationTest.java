package com.fulfillx.authservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fulfillx.authservice.security.JwtService;
import com.fulfillx.authservice.user.User;
import com.fulfillx.authservice.user.UserRepository;
import com.fulfillx.authservice.user.UserRole;
import com.fulfillx.authservice.user.UserStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end through the real Spring Security filter chain and a real
 * PostgreSQL instance (Testcontainers, version aligned with
 * docker-compose.yml's postgres:18-alpine per project convention — never
 * H2 as the only proof of Postgres behavior).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void shouldRegisterNewUserSuccessfully() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "correct-horse"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldRejectDuplicateNormalizedEmail() throws Exception {
        String email = uniqueEmail();
        String mixedCaseEmail = email.replace("user-", "USER-");

        register(email, "correct-horse");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", mixedCaseEmail, "password", "correct-horse"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void shouldRejectInvalidRegistrationPayload() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isNotEmpty());
    }

    @Test
    void shouldStorePasswordAsSecureHashNotPlaintext() throws Exception {
        String email = uniqueEmail();
        String rawPassword = "correct-horse-battery-staple";

        register(email, rawPassword);

        User stored = userRepository.findByEmail(email).orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo(rawPassword);
        assertThat(stored.getPasswordHash()).startsWith("$2"); // BCrypt
        assertThat(passwordEncoder.matches(rawPassword, stored.getPasswordHash())).isTrue();
    }

    @Test
    void shouldLoginSuccessfullyAndIssueAValidJwt() throws Exception {
        String email = uniqueEmail();
        register(email, "correct-horse");

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "correct-horse"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("accessToken").asText();
        User stored = userRepository.findByEmail(email).orElseThrow();

        assertThat(jwtService.validateAndParse(token).getSubject()).isEqualTo(stored.getId().toString());
    }

    @Test
    void shouldRejectLoginWithIncorrectPassword() throws Exception {
        String email = uniqueEmail();
        register(email, "correct-horse");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldRejectLoginForDisabledUser() throws Exception {
        String email = uniqueEmail();
        register(email, "correct-horse");

        User user = userRepository.findByEmail(email).orElseThrow();
        disableDirectlyInDatabase(user.getId());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "correct-horse"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void shouldRejectMeWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void shouldReturnCurrentUserForValidToken() throws Exception {
        String email = uniqueEmail();
        register(email, "correct-horse");
        String token = login(email, "correct-horse");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void shouldRejectMeWithTamperedToken() throws Exception {
        String email = uniqueEmail();
        register(email, "correct-horse");
        String token = login(email, "correct-horse");
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void shouldRejectMeWithExpiredToken() throws Exception {
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "CUSTOMER")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(1)))
                .signWith(testSigningKey())
                .compact();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void shouldEnforceUniqueEmailConstraintAtTheDatabaseLevel() {
        String email = uniqueEmail();
        userRepository.saveAndFlush(new User(email, "hash-a", UserRole.CUSTOMER));

        assertThatDataIntegrityViolation(() ->
                userRepository.saveAndFlush(new User(email, "hash-b", UserRole.CUSTOMER)));
    }

    @Test
    void shouldRejectNonLowercaseEmailAtTheDatabaseLevel() {
        assertThatDataIntegrityViolation(() ->
                userRepository.saveAndFlush(new User("Mixed-Case@Example.com", "hash", UserRole.CUSTOMER)));
    }

    private void assertThatDataIntegrityViolation(Runnable action) {
        try {
            action.run();
            org.junit.jupiter.api.Assertions.fail("Expected DataIntegrityViolationException");
        } catch (DataIntegrityViolationException expected) {
            // expected — the database constraint did its job
        }
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private void disableDirectlyInDatabase(UUID userId) {
        // No admin endpoint exists yet to disable an account (out of scope
        // for Phase 2A), so the test reaches into the repository directly —
        // legitimate here since the point is to prove login-time rejection,
        // not how a user comes to be disabled.
        userRepository.findById(userId).ifPresent(user -> {
            var status = UserStatus.DISABLED;
            setStatus(user, status);
            userRepository.saveAndFlush(user);
        });
    }

    private static void setStatus(User user, UserStatus status) {
        try {
            var field = User.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(user, status);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SecretKey testSigningKey() {
        // Must match src/test/resources/application.yml's auth.jwt.secret.
        return Keys.hmacShaKeyFor("test-only-secret-never-used-outside-this-test-suite-32bytes-min"
                .getBytes(StandardCharsets.UTF_8));
    }
}
