package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.value.Credentials;

import java.util.Objects;
import java.util.Optional;

/**
 * Domain service for password-based authentication.
 * Focused solely on verifying user credentials.
 */
public final class AuthenticationService {
    private final Ports.PasswordHasher passwordHasher;

    public AuthenticationService(final Ports.PasswordHasher passwordHasher) {
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
    }

    /**
     * Authenticate a user by verifying password.
     * Returns Optional containing authenticated user if password matches, empty if invalid.
     * Password is automatically cleared via Credentials.close() in try-with-resources.
     */
    public Optional<User> getAuthenticatedUser(final Credentials credentials) {
        try (final Credentials creds = credentials) {
            return passwordHasher.verify(creds);
        }
    }
}
