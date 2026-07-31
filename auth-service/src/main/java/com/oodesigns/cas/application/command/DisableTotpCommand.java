package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.UserId;
import java.util.Objects;

/**
 * Command to disable TOTP 2FA for a user.
 * <p>
 * Requires:
 * - User must re-authenticate (password verification required)
 * - User must have TOTP currently enabled
 * - Must specify reason for disable (audit trail context)
 * <p>
 * Effects:
 * - Removes TOTP secret from totp_secrets table
 * - Wipes all backup codes (single-use recovery codes)
 * - Emits audit event 'TOTP_DISABLED' with disable reason
 * - User can re-enroll 2FA anytime
 * <p>
 * The `reason` field is mandatory to provide audit context:
 * - Did user voluntarily disable? (USER_REQUESTED)
 * - Was it an admin action? (ADMIN_FORCED)
 * - Was it a security response? (SECURITY_INCIDENT)
 * - Was it a device recovery? (RECOVERY_FLOW)
 */
public record DisableTotpCommand(UserId userId, String password, DisableReason reason) {
    /**
     * Compact constructor validates all required fields.
     * @throws NullPointerException if any field is null
     * @throws IllegalArgumentException if password is blank
     */
    public DisableTotpCommand {
        Objects.requireNonNull(userId, "User ID is required");
        Objects.requireNonNull(password, "Password is required for re-authentication");
        Objects.requireNonNull(reason, "Disable reason is required for audit trail");
        if (password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be blank");
        }
    }
}

