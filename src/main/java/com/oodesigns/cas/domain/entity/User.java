package com.oodesigns.cas.domain.entity;

import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.Username;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Domain entity representing an authenticated user for authorization.
 * Immutable and minimal - contains only what's needed after authentication.
 * Password hash is NOT stored here; use UserCredential for authentication.
 * <p>
 * Includes password reset requirement and 2FA enforcement flags for login flow control.
 */
public record User(UserId userId, Username username, Set<Permission> permissions,
                   Instant passwordResetRequiredAt, Instant mfaRequiredAt) {
    public User {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(permissions, "permissions cannot be null");
        // Make permissions unmodifiable
        permissions = Set.copyOf(permissions);
        // passwordResetRequiredAt and mfaRequiredAt can be null (optional enforcement)
    }

    @Override
    public String toString() {
        return String.format("User{userId=%s, username=%s}", userId, username);
    }
}
