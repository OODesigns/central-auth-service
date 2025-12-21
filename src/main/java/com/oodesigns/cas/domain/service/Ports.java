package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.value.PasswordHash;
import com.oodesigns.cas.domain.value.Jti;
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
        boolean verify(final String token);
        String getPayload(final String token);
    }

    /**
     * Port for clock/time operations.
     */
    public interface Clock {
        Instant now();
    }

    /**
     * Port for rate limiting.
     */
    public interface RateLimiter {
        void checkLimit(final String key) throws RateLimitExceededException;
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(final String message) {
            super(message);
        }
    }
}
