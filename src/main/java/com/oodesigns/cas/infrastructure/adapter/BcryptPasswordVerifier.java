package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Credentials;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.PasswordHash;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.Optional;

/**
 * Production implementation of PasswordVerifier using Spring Security's BCrypt.
 * Verifies password credentials against stored password hashes using the bcrypt algorithm.
 * <p>
 * Security Properties:
 * - Uses BCrypt with salting and work factor for resistance against rainbow table and brute force attacks
 * - Spring Security's BCryptPasswordEncoder uses constant-time comparison to prevent timing attacks
 * - Password is cleared via Credentials.close() when used with try-with-resources
 * - Avoids logging or exposing verification failure reasons
 * - Supports all bcrypt hash formats: $2a$, $2b$, $2y$
 * <p>
 * Requires Spring Security: org.springframework.security:spring-security-crypto:6.3.0
 */
public final class BcryptPasswordVerifier implements Ports.PasswordVerifier, Ports.PasswordHasher {
    private final PasswordEncoder encoder;

    public BcryptPasswordVerifier() {
        this(new BCryptPasswordEncoder());
    }

    public BcryptPasswordVerifier(final PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    /**
     * Verify credentials by checking password against stored bcrypt hash.
     * 
     * @param credentials The credentials containing user credential and password to verify
     * @return Optional containing user ID if password matches, empty if invalid
     */
    @Override
    public Optional<UserId> verify(final Credentials credentials) {
        return Optional.ofNullable(credentials)
            .flatMap(this::authenticate);
    }

    /**
     * Authenticates the credentials and returns the User ID if successful.
     * Handles exceptions gracefully by returning empty.
     */
    private Optional<UserId> authenticate(final Credentials credentials) {
        try {
            return performBcryptCheck(credentials);
        } catch (final IllegalArgumentException _) {
            // Invalid bcrypt hash format or other illegal arguments
            // Return empty without logging to prevent information disclosure
            return Optional.empty();
        }
    }

    /**
     * Performs the actual BCrypt comparison.
     * Uses Spring Security BCryptPasswordEncoder for constant-time comparison.
     */
    private Optional<UserId> performBcryptCheck(final Credentials credentials) {
        final char[] passwordChars = credentials.password().chars();
        try {
            final String storedHash = credentials.credential().passwordHash().value();
            final boolean matches = encoder.matches(new String(passwordChars), storedHash);

            if (matches) {
                return Optional.of(credentials.credential().userId());
            }
            return Optional.empty();
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    @Override
    public PasswordHash hash(final Password password) {
        java.util.Objects.requireNonNull(password, "Password is required");
        final char[] passwordChars = password.chars();
        try {
            return PasswordHash.of(encoder.encode(new String(passwordChars)));
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }
}