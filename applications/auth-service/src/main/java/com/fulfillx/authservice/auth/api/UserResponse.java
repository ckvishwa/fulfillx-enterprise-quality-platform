package com.fulfillx.authservice.auth.api;

import com.fulfillx.authservice.user.User;
import com.fulfillx.authservice.user.UserRole;
import com.fulfillx.authservice.user.UserStatus;
import java.time.Instant;
import java.util.UUID;

/** Deliberately excludes {@code passwordHash} — never returned by the API. */
public record UserResponse(
        UUID id,
        String email,
        UserRole role,
        UserStatus status,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getStatus(), user.getCreatedAt());
    }
}
