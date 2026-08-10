package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.value.BackupCode;
import com.oodesigns.cas.domain.value.TotpCode;
import com.oodesigns.cas.domain.value.UserId;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application command handler for completing the 2FA login challenge.
 * <p>
 * Consumes the short-lived verification token issued by {@link LoginCommandHandler} when
 * a user with 2FA enabled logs in, and exchanges it for full access + refresh tokens
 * after the user proves second-factor possession.
 * <p>
 * Flow (security-ordered):
 * <ol>
 *   <li>Verify the 2FA verification token (signature, expiry, {@code aud: 2fa_verification})
 *       via {@link Ports.TokenVerifier}. Fails with {@code INVALID_VERIFICATION_TOKEN}.</li>
 *   <li>Verify the submitted code — OTP via {@link Ports.TotpVerifier#verifyCode}, or
 *       backup code via {@link Ports.TotpVerifier#verifyBackupCode} (consuming it).
 *       Fails with {@code INVALID_TOTP_CODE}.</li>
 *   <li>Load the full user object (permissions) via {@link Ports.UserRetriever}.
 *       Fails with {@code USER_NOT_FOUND}.</li>
 *   <li>Issue full access + refresh tokens via {@link TokenService#generateTokens}.
 *       Fails with {@code INTERNAL_ERROR} if signing fails.</li>
 * </ol>
 * <p>
 * SECURITY:
 * <ul>
 *   <li>The verification token is validated before any OTP check, so a missing or
 *       tampered token is rejected before touching infrastructure state.</li>
 *   <li>Backup codes are consumed (single-use) by the adapter on a successful
 *       {@code verifyBackupCode} call.</li>
 *   <li>Replay protection on the verification token (same JTI reused) is the adapter's
 *       responsibility (Phase 4.2 {@code invalidated_jwts} table).</li>
 * </ul>
 */
public final class VerifyTotpCommandHandler {

    private static final Logger LOGGER = Logger.getLogger(VerifyTotpCommandHandler.class.getName());
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private final Ports.TokenVerifier tokenVerifier;
    private final Ports.TotpVerifier totpVerifier;
    private final Ports.UserRetriever userRetriever;
    private final TokenService tokenService;

    /**
     * @param tokenVerifier  port for verifying the 2FA verification JWT
     * @param totpVerifier   port for OTP code and backup code verification
     * @param userRetriever  port for loading the full user (needed for token claims)
     * @param tokenService   domain service for generating access + refresh tokens
     */
    public VerifyTotpCommandHandler(final Ports.TokenVerifier tokenVerifier,
                                    final Ports.TotpVerifier totpVerifier,
                                    final Ports.UserRetriever userRetriever,
                                    final TokenService tokenService) {
        this.tokenVerifier = Objects.requireNonNull(tokenVerifier, "TokenVerifier is required");
        this.totpVerifier = Objects.requireNonNull(totpVerifier, "TotpVerifier is required");
        this.userRetriever = Objects.requireNonNull(userRetriever, "UserRetriever is required");
        this.tokenService = Objects.requireNonNull(tokenService, "TokenService is required");
    }

    /**
     * Handle the 2FA verification command.
     *
     * @param command the command with verification token and OTP/backup code; {@code null}
     *                returns an {@code INVALID_REQUEST} failure without touching any port
     * @return {@link VerifyTotpResult} with access + refresh tokens on success, or failure
     */
    public VerifyTotpResult handle(final VerifyTotpCommand command) {
        try {
            return Optional.ofNullable(command)
                .map(this::verify)
                .orElseGet(() -> VerifyTotpResult.failure("INVALID_REQUEST",
                    "VerifyTotpCommand cannot be null"));
        } catch (final RuntimeException e) {
            LOGGER.log(Level.SEVERE, INTERNAL_ERROR, e);
            return VerifyTotpResult.failure(INTERNAL_ERROR, "2FA verification failed: " + e.getMessage());
        }
    }

    private VerifyTotpResult verify(final VerifyTotpCommand command) {
        // Step 1: Validate the 2FA verification JWT
        final Optional<UserId> userIdOpt =
            tokenVerifier.verify2FAVerificationToken(command.verificationToken());
        if (userIdOpt.isEmpty()) {
            return VerifyTotpResult.failure("INVALID_VERIFICATION_TOKEN",
                "The 2FA verification token is expired or invalid. Please log in again.");
        }
        final UserId userId = userIdOpt.get();

        // Step 2: Verify OTP code or (single-use) backup code
        final boolean codeValid = command.isOtpCode()
            ? totpVerifier.verifyCode(userId, TotpCode.of(command.code()))
            : totpVerifier.verifyBackupCode(userId, BackupCode.of(command.code()));
        if (!codeValid) {
            return VerifyTotpResult.failure("INVALID_TOTP_CODE",
                "The submitted code is invalid. Please try again.");
        }

        // Step 3: Load user (needed for permission claims in the access token)
        final Optional<com.oodesigns.cas.domain.entity.User> userOpt = userRetriever.findById(userId);
        if (userOpt.isEmpty()) {
            return VerifyTotpResult.failure("USER_NOT_FOUND",
                "User account could not be located.");
        }

        // Step 4: Issue full access + refresh tokens
        return tokenService.generateTokens(userOpt.get())
            .map(tokens -> (VerifyTotpResult) VerifyTotpResult.success(
                tokens, userId, userOpt.get().permissions()))
            .orElseGet(() -> VerifyTotpResult.failure(INTERNAL_ERROR,
                "Failed to generate tokens."));
    }
}

