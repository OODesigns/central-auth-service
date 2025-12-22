package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.value.PasswordHash;
import java.time.Instant;

/**
 * Port interfaces for domain services.
 * Implementations are provided by the infrastructure layer.
 */
public class Ports {

    /**
     * Port for password hashing operations.
     */
    public interface PasswordHasher {
        PasswordHash hash(final String rawPassword);
        boolean verify(final String rawPassword, final PasswordHash hash);
    }

    /**
     * Port for token signing and verification.
     */
    public interface TokenSigner {
        String sign(final String payload, final Instant expiresAt);
    }

    /**
     * Port for clock/time operations.
     */
    public interface Clock {
        Instant now();
    }

    /**
     * Port for rate limiting.
     * Returns Optional containing error message if limit exceeded, empty if OK.
     */
    public interface RateLimiter {
        java.util.Optional<String> checkLimit(final String key);
    }

    /**
     * Port for reading user data.
     * Implementations handle DB/cache details.
     * Note: User creation/modification is outside the scope of authentication.
     */
    public interface UserRepositoryReader {
        java.util.Optional<com.oodesigns.cas.domain.entity.User> findByUsername(final com.oodesigns.cas.domain.value.Username username);
    }
}
