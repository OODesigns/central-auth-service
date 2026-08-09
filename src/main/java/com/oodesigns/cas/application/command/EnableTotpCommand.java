package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.UserId;
import java.util.Objects;

/**
 * Command to enable TOTP 2FA for a user by verifying the first OTP code.
 * <p>
 * This is step 2 of the enrolment flow (after {@link SetupTotpCommand}):
 * <ol>
 *   <li>SetupTotpCommandHandler generated a secret and returned an {@code otpauth://} URI.</li>
 *   <li><b>This command</b> — user scanned the QR code and submits the first OTP code.
 *       The handler verifies it against the stored secret, marks TOTP as active, and
 *       returns one-time-visible backup codes.</li>
 * </ol>
 * <p>
 * The {@code totpCode} must be the 6-digit code currently displayed by the authenticator
 * app. It is validated to match {@code ^\d{6}$} in the compact constructor so that an
 * obviously malformed value never reaches the infrastructure port.
 */
public record EnableTotpCommand(UserId userId, String totpCode) {
    /**
     * Compact constructor validates all required fields.
     *
     * @throws NullPointerException     if any field is null
     * @throws IllegalArgumentException if {@code totpCode} is not exactly 6 digits
     */
    public EnableTotpCommand {
        Objects.requireNonNull(userId, "User ID is required");
        Objects.requireNonNull(totpCode, "TOTP code is required");
        if (!totpCode.matches("^\\d{6}$")) {
            throw new IllegalArgumentException(
                "TOTP code must be exactly 6 digits (received: '" + totpCode + "')");
        }
    }
}

