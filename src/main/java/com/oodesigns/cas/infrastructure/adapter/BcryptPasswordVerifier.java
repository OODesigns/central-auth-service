package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Credentials;
import com.oodesigns.cas.domain.entity.User;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Optional;

/**
 * Production implementation of PasswordVerifier using BCrypt.
 * Verifies password credentials against stored password hashes using the bcrypt algorithm.
 * 
 * Security Properties:
 * - Uses BCrypt with salting and work factor for resistance against rainbow table and brute force attacks
 * - BCrypt.checkpw() uses constant-time comparison to prevent timing attacks
 * - Password is cleared via Credentials.close() when used with try-with-resources
 * - Avoids logging or exposing verification failure reasons
 * 
 * Requires the jbcrypt library: org.mindrot:jbcrypt:0.4
 */
public final class BcryptPasswordVerifier implements Ports.PasswordVerifier {

    /**
     * Verify credentials by checking password against stored bcrypt hash.
     * 
     * @param credentials The credentials containing user and password to verify
     * @return Optional containing authenticated user if password matches, empty if invalid
     */
    @Override
    public Optional<User> verify(final Credentials credentials) {
        return Optional.ofNullable(credentials)
            .flatMap(this::authenticateCredentials);
    }

    /**
     * Authenticate credentials by verifying password against stored bcrypt hash.
     * Uses BCrypt constant-time comparison to prevent timing attacks.
     * Password char[] is automatically cleared via Credentials AutoCloseable interface.
     * 
     * @param credentials the credentials to verify
     * @return Optional containing user if password matches, empty if invalid
     */
    private Optional<User> authenticateCredentials(final Credentials credentials) {
        try {
            // Convert char[] to String with minimal lifetime for BCrypt verification
            // Note: Java strings are immutable and will be GC'd after this block
            String providedPassword = new String(credentials.password().chars());
            final String storedHash = credentials.user().passwordHash().asString();
            
            // BCrypt.checkpw() performs constant-time comparison resistant to timing attacks
            final boolean matches = BCrypt.checkpw(providedPassword, storedHash);
            
            // Clear the password string from memory as soon as possible
            // Note: Java strings cannot be truly wiped, but setting to null enables GC sooner
            // This is intentional for security - SonarQube false positive
            providedPassword = null; // NOSONAR - intentional null assignment for security
            
            if (matches) {
                return Optional.of(credentials.user());
            }
            return Optional.empty();
            
        } catch (final RuntimeException e) {
            // Invalid bcrypt hash format, parsing error, or unexpected error
            // Return empty without logging to prevent information disclosure
            return Optional.empty();
        }
    }
}




