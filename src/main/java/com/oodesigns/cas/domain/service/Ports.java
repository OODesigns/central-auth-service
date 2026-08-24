package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.value.BackupCode;
import com.oodesigns.cas.domain.value.Jti;
import com.oodesigns.cas.domain.value.Credentials;
import com.oodesigns.cas.domain.value.Payload;
import com.oodesigns.cas.domain.value.TotpCode;
import com.oodesigns.cas.domain.value.UserCredential;
import com.oodesigns.cas.domain.value.Username;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.AccessToken;
import com.oodesigns.cas.domain.value.MfaEnrollmentToken;
import com.oodesigns.cas.domain.value.RefreshToken;
import com.oodesigns.cas.domain.value.TwoFactorVerificationToken;
import java.time.Instant;
import java.util.List;
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
        Optional<AccessToken> signAccessToken(final Payload payload, final Instant expiresAt);
        Optional<RefreshToken> signRefreshToken(final Payload payload, final Instant expiresAt);
        Optional<TwoFactorVerificationToken> signTwoFactorVerificationToken(final Payload payload, final Instant expiresAt);
        Optional<MfaEnrollmentToken> signMfaEnrollmentToken(final Payload payload, final Instant expiresAt);
    }

    /**
     * Port for verifying signed tokens issued by {@link TokenSigner}.
     * <p>
     * Implementations (in {@code infrastructure/}) use the same key material as
     * {@link TokenSigner} to validate signatures, expiry, and audience claims.
     * The domain/application layers never handle raw JWT bytes.
     */
    public interface TokenVerifier {
        /**
         * Verify an access token and extract the authenticated principal.
         *
         * @param token the compact JWT access token received from the client
         * @return Optional containing the authenticated principal if valid, empty otherwise
         */
        Optional<AccessTokenClaims> verifyAccessToken(AccessToken token);

        /**
         * Verify a 2FA verification token and extract the subject user ID.
         * <p>
         * A valid token must have:
         * <ul>
         *   <li>A trusted signature (same key as used by {@link TokenSigner}).</li>
         *   <li>An {@code exp} claim that has not yet passed.</li>
         *   <li>An {@code aud} claim equal to {@code "2fa_verification"}.</li>
         * </ul>
         *
         * @param token the compact JWT string received from the client
         * @return Optional containing the {@link UserId} from the {@code sub} claim if
         *         the token is valid; empty if the token is expired, has a bad signature,
         *         has the wrong audience, or is otherwise malformed
         */
        Optional<UserId> verify2FAVerificationToken(TwoFactorVerificationToken token);

        /** Verify the short-lived token used only to bootstrap required MFA enrollment. */
        Optional<UserId> verifyMfaEnrollmentToken(MfaEnrollmentToken token);

        /**
         * Verify a refresh token and extract the subject user ID.
         * <p>
         * A valid token must have:
         * <ul>
         *   <li>A trusted signature (same key as used by {@link TokenSigner}).</li>
         *   <li>An {@code exp} claim that has not yet passed.</li>
         *   <li>An {@code aud} claim equal to {@code "refresh_token"} — this distinguishes
         *       refresh tokens from access tokens ({@code aud: "access_token"}) and 2FA verification
         *       tokens ({@code aud: 2fa_verification}), preventing token-type confusion.</li>
         * </ul>
         * <p>
         * NOTE: signature/expiry validation here is a fast, DB-free first gate. The
         * authoritative check that the token is still <em>current</em> (not already rotated
         * or revoked) is performed atomically by {@link RefreshTokenStore#rotate}.
         *
         * @param token the compact JWT refresh token received from the client
         * @return Optional containing the {@link UserId} from the {@code sub} claim if the
         *         token is valid; empty otherwise
         */
        Optional<UserId> verifyRefreshToken(RefreshToken token);
    }

    /**
     * Claims extracted from a validated access token.
     */
    public record AccessTokenClaims(UserId userId, Jti jti, Instant expiresAt,
                                    java.util.Set<com.oodesigns.cas.domain.value.Permission> permissions) {
        public AccessTokenClaims {
            java.util.Objects.requireNonNull(userId, "UserId cannot be null");
            java.util.Objects.requireNonNull(jti, "JTI cannot be null");
            java.util.Objects.requireNonNull(expiresAt, "Expiry time cannot be null");
            java.util.Objects.requireNonNull(permissions, "Permissions cannot be null");
            permissions = java.util.Set.copyOf(permissions);
        }

        public AccessTokenClaims(final UserId userId, final Jti jti, final Instant expiresAt) {
            this(userId, jti, expiresAt, java.util.Set.of());
        }
    }

    /**
     * Port for invalidating and querying revoked access tokens.
     */
    public interface AccessTokenRevocationStore {
        void invalidate(final AccessTokenClaims claims, final AccessToken token, final String reason);

        boolean isInvalidated(final Jti jti);
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
     * Port for retrieving user credentials by user ID.
     * <p>
     * Used for <b>re-authentication</b> of an already-identified user (e.g. disabling 2FA,
     * changing a password), where the {@link UserId} originates from a verified session or
     * token rather than from client input.
     * <p>
     * SECURITY: This port is deliberately keyed by {@code UserId} instead of {@code Username}.
     * Re-authentication must never trust a client-supplied username, otherwise a caller could
     * present someone else's username together with a password they know and pass the check.
     * Callers must additionally confirm that the verified {@code UserId} equals the requested
     * one (defence in depth).
     */
    public interface UserCredentialByIdRetriever {
        /**
         * Look up the stored credential (user ID + password hash) for a user.
         *
         * @param userId the ID of the user being re-authenticated
         * @return Optional containing the credential, empty if the user does not exist
         */
        Optional<UserCredential> findCredentialsByUserId(final UserId userId);
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
     * Handles verification of codes from authenticator apps and single-use backup codes.
     */
    public interface TotpVerifier {
        /**
         * Verify a TOTP code against the user's registered secret.
         *
         * @param userId   the user ID
         * @param totpCode the 6-digit code entered by the user
         * @return true if code is valid and matches current time window, false otherwise
         */
        boolean verifyCode(final UserId userId, final TotpCode totpCode);

        /**
         * Verify the first OTP code during <b>enrolment</b>, against the user's <em>pending</em>
         * (not-yet-activated) TOTP secret.
         * <p>
         * SECURITY: This is deliberately distinct from {@link #verifyCode}, which only accepts
         * an <em>active</em> secret. A pending secret must never satisfy a login-time 2FA
         * challenge, so the enable flow uses this method while the login flow uses
         * {@link #verifyCode}.
         *
         * @param userId   the user completing enrolment
         * @param totpCode the first 6-digit code from the authenticator app
         * @return true if the code matches the pending secret's current time window
         */
        boolean verifySetupCode(final UserId userId, final TotpCode totpCode);

        /**
         * Verify and consume a backup code for account recovery.
         * A successfully verified code is immediately invalidated (single-use).
         *
         * @param userId     the user ID
         * @param backupCode the backup code to verify
         * @return true if valid and unused, false if invalid or already used
         */
        boolean verifyBackupCode(final UserId userId, final BackupCode backupCode);

        /**
         * Check if TOTP is enabled for the user.
         *
         * @param userId the user ID
         * @return true if 2FA is enabled, false otherwise
         */
        boolean isTotpEnabled(final UserId userId);
    }

    /**
     * Port for rate limiting 2FA verification attempts (per-user).
     * Implementations should apply limits to reject excessive verification attempts
     * for a given user to mitigate brute-force or abuse of backup-code redemption.
     */
    public interface TotpRateLimiter {
        /**
         * Check rate limits for a 2FA verification attempt for the given user.
         * @param userId the user being verified
         * @return RateLimitResult.allowed() if allowed, or RateLimitResult.blocked(message)
         */
        RateLimitResult checkLimit(final UserId userId);
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
         * Codes are returned as {@link BackupCode} value objects for one-time display.
         * The implementation stores BCrypt-hashed versions — plaintexts must not be
         * logged or re-transmitted by the delivery layer.
         *
         * @param userId the user ID
         * @return list of 10-16 backup codes
         */
        List<BackupCode> generateBackupCodes(final UserId userId);
    }

    /**
     * Port for persisting and rotating refresh tokens (backed by the {@code refresh_tokens}
     * table) to support long-lived sessions with automatic reuse detection.
     * <p>
     * SECURITY MODEL — rotating refresh tokens with family-based reuse detection:
     * <ul>
     *   <li>Only a one-way hash of each refresh token is stored (never the raw token).</li>
     *   <li>Every token belongs to a <em>family</em> created at login / 2FA completion.</li>
     *   <li>On each use the presented token is consumed and replaced by a fresh token in the
     *       same family ({@link #rotate}).</li>
     *   <li>If an already-consumed token is presented again, the whole family is revoked —
     *       a stolen-token replay can never outlive a single rotation.</li>
     * </ul>
     */
    public interface RefreshTokenStore {
        /**
         * Record a newly issued refresh token, starting a new token family.
         * Called after a successful login or 2FA verification.
         *
         * @param userId       the owner of the refresh token
         * @param refreshToken the raw refresh token (the implementation stores only its hash)
         */
        void issue(final UserId userId, final RefreshToken refreshToken);

        /**
         * Atomically consume the presented refresh token and record its replacement within the
         * same family. Detects reuse of an already-rotated/revoked token and revokes the family.
         *
         * @param presentedToken   the raw refresh token supplied by the client
         * @param replacementToken the raw refresh token that replaces it on success
         * @return the outcome of the rotation attempt
         */
        RotationStatus rotate(final RefreshToken presentedToken, final RefreshToken replacementToken);

        /**
         * Outcome of a {@link #rotate} attempt.
         */
        enum RotationStatus {
            /** Token was current; it has been consumed and replaced within its family. */
            ROTATED,
            /** Token was already consumed/revoked — replay detected; the family was revoked. */
            REUSE_DETECTED,
            /** No stored token matched the presented hash. */
            NOT_FOUND,
            /** The stored token existed but had already expired. */
            EXPIRED
        }
    }
}
