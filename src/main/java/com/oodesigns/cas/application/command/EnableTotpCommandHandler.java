package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.Ports;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application command handler for completing TOTP 2FA enrolment.
 * <p>
 * This is step 2 of the enrolment flow (after {@link SetupTotpCommandHandler}):
 * <ol>
 *   <li>Verify the user's first OTP code against the stored pending secret.</li>
 *   <li>If valid, mark TOTP as active ({@code totp_verified_at = now()}) via
 *       {@link Ports.TotpSetupProvider#enableTotp}.</li>
 *   <li>Generate and return one-time-visible backup codes via
 *       {@link Ports.TotpSetupProvider#generateBackupCodes}.</li>
 * </ol>
 * <p>
 * SECURITY:
 * <ul>
 *   <li>The OTP is verified <em>before</em> TOTP is marked active, so a failed enable
 *       attempt never changes any persistent state.</li>
 *   <li>Backup codes are returned as plaintext exactly once, for immediate display. The
 *       adapter stores BCrypt-hashed versions — the delivery layer must never log or
 *       cache the plaintext codes.</li>
 *   <li>Replay protection (rejecting a code used in the same 30-second window) is the
 *       adapter's responsibility.</li>
 * </ul>
 */
public final class EnableTotpCommandHandler {

    private static final Logger LOGGER = Logger.getLogger(EnableTotpCommandHandler.class.getName());
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private final Ports.TotpVerifier totpVerifier;
    private final Ports.TotpSetupProvider totpSetupProvider;

    /**
     * @param totpVerifier      port for OTP code verification
     * @param totpSetupProvider port for enabling TOTP and generating backup codes
     */
    public EnableTotpCommandHandler(final Ports.TotpVerifier totpVerifier,
                                    final Ports.TotpSetupProvider totpSetupProvider) {
        this.totpVerifier = Objects.requireNonNull(totpVerifier, "TotpVerifier is required");
        this.totpSetupProvider = Objects.requireNonNull(totpSetupProvider, "TotpSetupProvider is required");
    }

    /**
     * Handle the enable command: verify OTP, activate TOTP, return backup codes.
     *
     * @param command the enable command with user ID and the first OTP code; {@code null}
     *                returns an {@code INVALID_REQUEST} failure
     * @return {@link EnableTotpResult} with one-time backup codes on success, or failure
     */
    public EnableTotpResult handle(final EnableTotpCommand command) {
        try {
            return Optional.ofNullable(command)
                .map(this::enableTotp)
                .orElseGet(() -> EnableTotpResult.failure("INVALID_REQUEST",
                    "EnableTotpCommand cannot be null"));
        } catch (final RuntimeException e) {
            LOGGER.log(Level.SEVERE, INTERNAL_ERROR, e);
            return EnableTotpResult.failure(INTERNAL_ERROR, "Failed to enable TOTP: " + e.getMessage());
        }
    }

    private EnableTotpResult enableTotp(final EnableTotpCommand command) {
        // Step 1: Verify the submitted OTP against the PENDING secret before touching state.
        // Uses verifySetupCode (pending secret) — not verifyCode (active secret) — so an
        // in-progress enrolment is validated without exposing the pending secret to login.
        if (!totpVerifier.verifySetupCode(command.userId(), command.totpCode())) {
            return EnableTotpResult.failure("INVALID_TOTP_CODE",
                "The submitted TOTP code is invalid. Please check your authenticator app and try again.");
        }

        // Step 2: Mark TOTP as active (sets totp_verified_at)
        if (!totpSetupProvider.enableTotp(command.userId())) {
            return EnableTotpResult.failure("TOTP_ALREADY_ENABLED",
                "TOTP is already enabled for this user.");
        }

        // Step 3: Generate one-time-visible backup codes (adapter stores BCrypt hashes)
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "TOTP enabled for user {0}", command.userId());
        }
        return EnableTotpResult.success(totpSetupProvider.generateBackupCodes(command.userId()));
    }
}

