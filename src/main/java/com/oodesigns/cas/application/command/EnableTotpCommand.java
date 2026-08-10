package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.TotpCode;
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
 *
 * @param userId   the user for whom TOTP is being enabled
 * @param totpCode the first 6-digit TOTP code from the authenticator app, validated by
 *                 the {@link TotpCode} value object
 */
public record EnableTotpCommand(UserId userId, TotpCode totpCode) {
    /**
     * Compact constructor validates all required fields.
     *
     * @throws NullPointerException if any field is null
     */
    public EnableTotpCommand {
        Objects.requireNonNull(userId, "User ID is required");
        Objects.requireNonNull(totpCode, "TOTP code is required");
    }
}

