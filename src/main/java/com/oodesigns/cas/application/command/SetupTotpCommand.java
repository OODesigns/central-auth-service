package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.Username;
import java.util.Objects;

/**
 * Command to initiate TOTP 2FA setup for a user.
 * <p>
 * The setup flow is:
 * <ol>
 *   <li>SetupTotpCommandHandler generates a secret and returns an {@code otpauth://} URI
 *       (this command).</li>
 *   <li>The user scans the QR code with an authenticator app.</li>
 *   <li>The user submits the first OTP code via {@code EnableTotpCommandHandler} to confirm
 *       the device is properly configured.</li>
 * </ol>
 * <p>
 * The {@code username} is required because it appears in the {@code otpauth://} URI label
 * so the user can identify the account in their authenticator app.
 */
public record SetupTotpCommand(UserId userId, Username username) {
    /**
     * Compact constructor validates all required fields.
     *
     * @throws NullPointerException if any field is null
     */
    public SetupTotpCommand {
        Objects.requireNonNull(userId, "User ID is required");
        Objects.requireNonNull(username, "Username is required for the otpauth:// URI label");
    }
}

