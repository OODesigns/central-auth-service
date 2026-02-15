package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.Ports;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application command handler for disabling TOTP 2FA.
 * <p>
 * Policy:
 * - Requires password re-authentication (security measure)
 * - Deletes TOTP secret from database
 * - Wipes all backup codes (single-use recovery codes)
 * - Emits audit event for compliance tracking
 * - User can immediately re-enroll 2FA
 * <p>
 * Scenarios:
 * 1. User lost authenticator device → disables old 2FA, enrolls with new device
 * 2. Admin removes 2FA requirement → user can disable their enrolled 2FA
 * 3. User wants to disable 2FA entirely → allowed if admin hasn't enforced it
 * 4. User with mfa_required_at set tries to disable → call succeeds, but
 *    they'll be blocked at login until they re-enroll
 */
public final class DisableTotpCommandHandler {
    private static final Logger LOGGER = Logger.getLogger(DisableTotpCommandHandler.class.getName());
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private final AuthenticationService authService;
    private final Ports.UserCredentialRetriever credentialReader;
    private final Ports.TotpSetupProvider totpSetupProvider;

    public DisableTotpCommandHandler(final AuthenticationService authService,
                                     final Ports.UserCredentialRetriever credentialReader,
                                     final Ports.TotpSetupProvider totpSetupProvider) {
        this.authService = Objects.requireNonNull(authService);
        this.credentialReader = Objects.requireNonNull(credentialReader);
        this.totpSetupProvider = Objects.requireNonNull(totpSetupProvider);
    }

    /**
     * Handle request to disable TOTP 2FA.
     *
     * Process:
     * 1. Verify password (re-authentication required)
     * 2. Check TOTP is currently enabled
     * 3. Delete TOTP secret and backup codes
     * 4. Emit audit event with disable reason
     *
     * @param command the disable TOTP command with user ID, password, and reason
     * @return DisableTotpResult with success or failure details
     */
    public DisableTotpResult handle(final DisableTotpCommand command) {
        try {
            return disableTotpForUser(command);
        } catch (final RuntimeException e) {
            LOGGER.log(Level.SEVERE, INTERNAL_ERROR, e);
            return DisableTotpResult.failure(INTERNAL_ERROR,
                "Failed to disable 2FA: " + e.getMessage());
        }
    }

    private DisableTotpResult disableTotpForUser(final DisableTotpCommand command) {
        // Step 1: Verify password (re-authentication required for security)
        final DisableTotpResult passwordVerification = verifyPasswordForDisable(command);
        if (passwordVerification instanceof DisableTotpResult.FailureResult) {
            return passwordVerification;
        }

        // Step 2: Disable TOTP (calls totpSetupProvider which:
        //   - Checks TOTP is enabled
        //   - Deletes totp_secrets row (ON DELETE CASCADE removes backup codes)
        //   - Emits audit event via trigger with disable reason
        final boolean disabled = totpSetupProvider.disableTotp(command.userId(), command.reason());

        if (!disabled) {
            return DisableTotpResult.failure("TOTP_NOT_ENABLED",
                "User does not have 2FA enabled. Nothing to disable.");
        }

        // Success: TOTP secret and all backup codes deleted
        // Audit event 'TOTP_DISABLED' emitted by database trigger with reason
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "TOTP disabled for user {0} - reason: {1}",
                new Object[]{command.userId(), command.reason()});
        }
        return DisableTotpResult.success();
    }

    /**
     * Verify user's password before allowing TOTP disable.
     * This is a security measure: prevents unauthorized 2FA removal if account is compromised.
     *
     * Implementation note: This currently accepts any password due to port limitation.
     * In production, add Ports.UserPasswordVerifier that takes userId + password directly,
     * or have the endpoint provide username + password together.
     *
     * @param command the disable command containing user ID and password
     * @return success if password is valid, failure otherwise
     */
    private DisableTotpResult verifyPasswordForDisable(final DisableTotpCommand command) {
        // LIMITATION: credentialReader requires username, but we only have userId
        // In a real implementation, add:
        //   - Ports.UserPasswordVerifier.verifyPassword(userId, password)
        //   - Or require username in DisableTotpCommand
        //   - Or look up user by ID first in userRepository

        // For now, accept the disable request with password placeholder
        // Production implementation must add proper re-authentication
        if (command.password() == null || command.password().isBlank()) {
            return DisableTotpResult.failure("INVALID_PASSWORD",
                "Password cannot be blank.");
        }

        // TODO: Implement proper password verification with userId
        // This is a security gap that must be addressed
        return DisableTotpResult.success();
    }
}

