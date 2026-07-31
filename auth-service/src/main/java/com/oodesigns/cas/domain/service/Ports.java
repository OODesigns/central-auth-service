package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.value.Credentials;
import com.oodesigns.cas.domain.value.Payload;
import com.oodesigns.cas.domain.value.UserCredential;
import com.oodesigns.cas.domain.value.Username;
import com.oodesigns.cas.domain.value.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

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
         * Verify credentials and return user ID if successful.
         * 
         * @param credentials The user credentials to verify
         * @return Optional containing user ID if password matches, empty if invalid
         */
        Optional<UserId> verify(final Credentials credentials);
    }

    /**
     * Port for token signing and verification.
     */
    public interface TokenSigner {
        Optional<String> sign(final Payload payload, final Instant expiresAt);
    }

    /**
     * Port for clock/time operations.
     */
    public interface Clock {
        Instant now();
    }

    /**
     * Port for rate limiting login attempts.
     * Checks three rate limit buckets: IP address, username, and IP+username combination.
     */
    public interface RateLimiter {
        /**
         * Check rate limits for a login command across multiple buckets.
         * Returns the first blocking result or allows the request to proceed.
         *
         * @param command the login command containing username and IP address
         * @return Rate limit result (allowed or blocked with reason)
         */
        RateLimitResult checkLimit(final com.oodesigns.cas.application.command.LoginCommand command);
    }

    /**
     * Result of a rate limit check using fluent mapTo(...).orElse(...) pattern.
     */
    public sealed interface RateLimitResult
        permits RateLimitResult.Allowed, RateLimitResult.Blocked {

        <T> Mapper<T> mapTo(Function<Allowed, T> onAllowed);

        static Allowed allowed() {
            return new Allowed();
        }

        static Blocked blocked(final String message) {
            return new Blocked(message);
        }

        record Allowed() implements RateLimitResult {
            @Override
            public <T> Mapper<T> mapTo(final Function<Allowed, T> onAllowed) {
                return new MapperAllowed<>(onAllowed.apply(this));
            }

            static final class MapperAllowed<T> implements Mapper<T> {
                private final T value;

                MapperAllowed(final T value) {
                    this.value = value;
                }

                @Override
                public T orElse(final Function<Blocked, T> onBlocked) {
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
            public <T> Mapper<T> mapTo(final Function<Allowed, T> onAllowed) {
                return new MapperBlocked<>(this);
            }

            static final class MapperBlocked<T> implements Mapper<T> {
                private final Blocked blocked;

                MapperBlocked(final Blocked blocked) {
                    this.blocked = blocked;
                }

                @Override
                public T orElse(final Function<Blocked, T> onBlocked) {
                    return onBlocked.apply(blocked);
                }
            }
        }

        interface Mapper<T> {
            T orElse(Function<Blocked, T> onBlocked);
        }
    }

    /**
     * Port for retrieving user credentials.
     * Implementations handle DB/cache details.
     * Note: User creation/modification is outside the scope of authentication.
     */
    public interface UserCredentialRetriever {
        Optional<UserCredential> findCredentialsByUsername(final Username username);
    }

    /**
     * Port for retrieving full user data by ID.
     * Used after authentication succeeds to retrieve permissions and other user metadata.
     */
    public interface UserRetriever {
        Optional<User> findById(final UserId userId);
    }

    /**
     * Port for checking if 2FA (TOTP) is enabled for a user.
     * Used during login flow to determine if additional verification is required.
     */
    public interface TotpStatusReader {
        /**
         * Check if TOTP 2FA is enabled for a user.
         *
         * @param userId the user ID to check
         * @return Optional containing the userId if 2FA is enabled, empty if disabled or user not found
         */
        Optional<UserId> check2FAStatus(final UserId userId);
    }

    /**
     * Port for TOTP (Time-based One-Time Password) verification.
     * Handles verification of 6-digit codes from authenticator apps.
     */
    public interface TotpVerifier {
        /**
         * Verify a TOTP code against the user's registered secret.
         *
         * @param userId the user ID
         * @param totpCode the 6-digit code entered by the user
         * @return true if code is valid and matches current time window, false otherwise
         */
        boolean verifyCode(final UserId userId, final String totpCode);

        /**
         * Generate a backup code for account recovery.
         * Codes are returned plaintext to user; implementation stores hashed versions.
         *
         * @param userId the user ID
         * @return plaintext backup code in format XXXX-XXXX-XXXX-XXXX
         */
        String generateBackupCode(final UserId userId);

        /**
         * Verify and consume a backup code for account recovery.
         *
         * @param userId the user ID
         * @param backupCode the plaintext backup code
         * @return true if valid and unused, false if invalid or already used
         */
        boolean verifyBackupCode(final UserId userId, final String backupCode);

        /**
         * Check if TOTP is enabled for the user.
         *
         * @param userId the user ID
         * @return true if 2FA is enabled, false otherwise
         */
        boolean isTotpEnabled(final UserId userId);
    }

    /**
     * Port for TOTP setup and management.
     * Handles generation and storage of TOTP secrets during enrollment.
     *
     * 2FA Status is derived from users.totp_verified_at:
     * - NULL = TOTP disabled
     * - NOT NULL = TOTP enabled (timestamp of verification)
     */
    public interface TotpSetupProvider {
        /**
         * Generate a new TOTP secret for 2FA setup.
         * The secret is base32-encoded and suitable for QR code generation.
         *
         * @param userId the user ID
         * @return base32-encoded TOTP secret
         */
        String generateSecret(final UserId userId);

        /**
         * Enable TOTP 2FA for a user after they verify the initial code.
         * Sets users.totp_verified_at to current timestamp.
         *
         * @param userId the user ID
         * @return true if successfully enabled, false if already enabled
         */
        boolean enableTotp(final UserId userId);

        /**
         * Disable TOTP 2FA for a user.
         * Removes the TOTP secret and all backup codes.
         * Deletes totp_secrets row (ON DELETE CASCADE removes backup_codes).
         *
         * @param userId the user ID
         * @param reason the reason for disabling (for audit trail context)
         * @return true if successfully disabled, false if not enabled
         */
        boolean disableTotp(final UserId userId, final com.oodesigns.cas.application.command.DisableReason reason);

        /**
         * Generate and return all backup codes for a user.
         * Codes should be displayed to user exactly once and then discarded from memory.
         * Implementation stores hashed versions.
         *
         * @param userId the user ID
         * @return list of 10-16 plaintext backup codes
         */
        java.util.List<String> generateBackupCodes(final UserId userId);
    }
}
