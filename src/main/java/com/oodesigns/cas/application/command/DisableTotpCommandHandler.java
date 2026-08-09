package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Credentials;
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
 * <p>
 * SECURITY: Re-authentication is keyed by {@link com.oodesigns.cas.domain.value.UserId}
 * via {@link Ports.UserCredentialByIdRetriever}, never by a client-supplied username.
 * Disabling 2FA is a security-downgrading operation, so an attacker holding a stolen
 * session must still prove knowledge of the account password before it is permitted.
 */
public final class DisableTotpCommandHandler {
    private static final Logger LOGGER = Logger.getLogger(DisableTotpCommandHandler.class.getName());
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    private static final String INVALID_PASSWORD = "INVALID_PASSWORD";

    private final AuthenticationService authService;
    private final Ports.UserCredentialByIdRetriever credentialReader;
    private final Ports.TotpSetupProvider totpSetupProvider;

    public DisableTotpCommandHandler(final AuthenticationService authService,
                                     final Ports.UserCredentialByIdRetriever credentialReader,
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
        // Step 1: Re-authenticate (mandatory — disabling 2FA weakens the account)
        if (!isReAuthenticated(command)) {
            return DisableTotpResult.failure(INVALID_PASSWORD,
                "Password verification failed. Re-authentication is required to disable 2FA.");
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
     * Verify the user's password before allowing TOTP disable.
     * <p>
     * Looks up the stored credential by {@code userId} (never by a client-supplied username),
     * then delegates the actual hash comparison to {@link AuthenticationService}, reusing the
     * same constant-time BCrypt path as login. The plaintext password is zeroed by
     * {@link Credentials#close()} inside the authentication service.
     * <p>
     * Defence in depth: the verified {@code UserId} returned by the verifier must match the
     * user being modified, so a credential-lookup mismatch can never authorise a disable.
     *
     * @param command the disable command containing user ID and password
     * @return true when the password is valid for that exact user, false otherwise
     */
    private boolean isReAuthenticated(final DisableTotpCommand command) {
        return credentialReader.findCredentialsByUserId(command.userId())
            .map(credential -> Credentials.of(credential, command.password()))
            .flatMap(authService::getAuthenticatedUser)
            .filter(verifiedUserId -> verifiedUserId.equals(command.userId()))
            .isPresent();
    }
}

