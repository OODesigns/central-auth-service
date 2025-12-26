package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.value.Credentials;
import java.time.Instant;
import java.util.Optional;

/**
 * Port interfaces for domain services.
 * Implementations are provided by the infrastructure layer.
 */
public class Ports {

    /**
     * Port for password verification and hashing operations.
     * Primary responsibility is verifying user credentials during authentication.
     */
    public interface PasswordVerifier {     
       /**
         * Verify credentials and return authenticated user if successful.
         * 
         * @param credentials The user credentials to verify
         * @return Optional containing user if password matches, empty if invalid
         */
        Optional<com.oodesigns.cas.domain.entity.User> verify(final Credentials credentials);
    }

    /**
     * Port for token signing and verification.
     */
    public interface TokenSigner {
        java.util.Optional<String> sign(final com.oodesigns.cas.domain.value.Payload payload, final Instant expiresAt);
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
        RateLimitResult checkLimit(final String key);
    }

    /**
     * Result of a rate limit check using fluent mapTo(...).orElse(...) pattern.
     */
    public sealed interface RateLimitResult
        permits RateLimitResult.Allowed, RateLimitResult.Blocked {

        <T> Mapper<T> mapTo(java.util.function.Function<Allowed, T> onAllowed);

        static Allowed allowed() {
            return new Allowed();
        }

        static Blocked blocked(final String message) {
            return new Blocked(message);
        }

        record Allowed() implements RateLimitResult {
            @Override
            public <T> Mapper<T> mapTo(java.util.function.Function<Allowed, T> onAllowed) {
                return new MapperAllowed<>(onAllowed.apply(this));
            }

            static final class MapperAllowed<T> implements Mapper<T> {
                private final T value;

                MapperAllowed(T value) {
                    this.value = value;
                }

                @Override
                public T orElse(java.util.function.Function<Blocked, T> onBlocked) {
                    return value;
                }
            }
        }

        record Blocked(String message) implements RateLimitResult {
            public Blocked {
                if (message == null || message.isBlank()) {
                    throw new IllegalArgumentException("Blocked message is required");
                }
            }

            @Override
            public <T> Mapper<T> mapTo(java.util.function.Function<Allowed, T> onAllowed) {
                return new MapperBlocked<>(this);
            }

            static final class MapperBlocked<T> implements Mapper<T> {
                private final Blocked blocked;

                MapperBlocked(Blocked blocked) {
                    this.blocked = blocked;
                }

                @Override
                public T orElse(java.util.function.Function<Blocked, T> onBlocked) {
                    return onBlocked.apply(blocked);
                }
            }
        }

        interface Mapper<T> {
            T orElse(java.util.function.Function<Blocked, T> onBlocked);
        }
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
