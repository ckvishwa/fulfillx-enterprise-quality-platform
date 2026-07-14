package com.fulfillx.authservice.auth;

import com.fulfillx.authservice.auth.api.AuthResponse;
import com.fulfillx.authservice.auth.api.LoginRequest;
import com.fulfillx.authservice.auth.api.RegisterRequest;
import com.fulfillx.authservice.auth.api.UserResponse;
import com.fulfillx.authservice.security.JwtService;
import com.fulfillx.authservice.user.User;
import com.fulfillx.authservice.user.UserRepository;
import com.fulfillx.authservice.user.UserRole;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Public registration always creates a {@link UserRole#CUSTOMER}
     * account — the request body has no role field, so a caller can never
     * self-assign OPERATOR/ADMIN. Those roles are provisioned out of band
     * (no such endpoint exists yet; see the Phase 2A ADR).
     */
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = normalize(request.email());

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = new User(normalizedEmail, passwordEncoder.encode(request.password()), UserRole.CUSTOMER);
        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // Two concurrent registrations for the same email can both pass
            // the findByEmail check above before either commits; the
            // database's unique constraint is the real guard, this just
            // keeps the API contract consistent under that race.
            throw new EmailAlreadyRegisteredException();
        }
    }

    /**
     * Password is checked before account status is even looked at. An
     * attacker who doesn't know the password gets the same
     * "invalid credentials" response whether the account exists, is
     * active, locked, or disabled — status is only revealed once someone
     * has already proven they know the password, which is safe UX rather
     * than an information leak.
     */
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalize(request.email());

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isActive()) {
            throw new AccountNotActiveException(user.getStatus());
        }

        String token = jwtService.issue(user);
        return new AuthResponse(token, "Bearer", jwtService.expirationSeconds(), UserResponse.from(user));
    }

    public UserResponse currentUser(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(InvalidCredentialsException::new);
    }

    private String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
