package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.value.Credentials;
import com.oodesigns.cas.domain.value.UserId;

import java.util.Objects;
import java.util.Optional;

/**
 * Domain service for password-based authentication.
 * Focused solely on verifying user credentials.
 */
public final class AuthenticationService {
    private final Ports.PasswordVerifier passwordVerifier;

    public AuthenticationService(final Ports.PasswordVerifier passwordVerifier) {
        this.passwordVerifier = Objects.requireNonNull(passwordVerifier);
    }

    /**
     * Authenticate a user by verifying password.
     * Returns Optional containing user ID if password matches, empty if invalid.
     * Password is automatically cleared via Credentials.close() in try-with-resources.
     */
    public Optional<UserId> getAuthenticatedUser(final Credentials credentials) {
        try (final Credentials creds = credentials) {
            return passwordVerifier.verify(creds);
        }
    }
}
