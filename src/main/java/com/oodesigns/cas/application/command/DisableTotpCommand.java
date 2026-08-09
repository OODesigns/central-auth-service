package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.Password;
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
 * <p>
 * SECURITY: {@code userId} must originate from a verified session/token, never from
 * client-supplied input. The password is carried as a {@link Password} value object
 * (char[] backed, zeroed after verification) rather than a String, so the plaintext
 * can be wiped from memory once re-authentication completes.
 */
public record DisableTotpCommand(UserId userId, Password password, DisableReason reason) {
    /**
     * Compact constructor validates all required fields.
     * Password content rules (length, non-blank) are enforced by {@link Password#of}.
     *
     * @throws NullPointerException if any field is null
     */
    public DisableTotpCommand {
        Objects.requireNonNull(userId, "User ID is required");
        Objects.requireNonNull(password, "Password is required for re-authentication");
        Objects.requireNonNull(reason, "Disable reason is required for audit trail");
    }
}

