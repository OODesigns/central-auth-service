package com.oodesigns.cas.application.command;

import java.util.Objects;

/**
 * Command to complete 2FA verification after login.
 * <p>
 * This command is the application of a {@link LoginResult.Required2FAResult}: the client
 * holds a short-lived verification token (5 min, {@code aud: 2fa_verification}) and must
 * prove second-factor possession by submitting either a live TOTP code or a backup code.
 * <p>
 * The {@code code} field accepts two formats (validated in the compact constructor):
 * <ul>
 *   <li><b>TOTP OTP:</b> exactly 6 decimal digits, e.g. {@code "123456"}</li>
 *   <li><b>Backup code:</b> {@code XXXX-XXXX-XXXX-XXXX} (uppercase alphanumeric + dashes),
 *       e.g. {@code "ABCD-EFGH-IJKL-MNOP"}</li>
 * </ul>
 * Any other format is rejected before reaching the infrastructure ports.
 */
public record VerifyTotpCommand(String verificationToken, String code) {

    private static final String OTP_PATTERN = "^\\d{6}$";
    private static final String BACKUP_CODE_PATTERN = "^[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}$";

    /**
     * Compact constructor validates all required fields.
     *
     * @throws NullPointerException     if any field is null
     * @throws IllegalArgumentException if {@code verificationToken} is blank, or
     *                                  {@code code} is neither a 6-digit OTP nor a
     *                                  XXXX-XXXX-XXXX-XXXX backup code
     */
    public VerifyTotpCommand {
        Objects.requireNonNull(verificationToken, "Verification token is required");
        Objects.requireNonNull(code, "Code is required");
        if (verificationToken.isBlank()) {
            throw new IllegalArgumentException("Verification token cannot be blank");
        }
        if (!code.matches(OTP_PATTERN) && !code.matches(BACKUP_CODE_PATTERN)) {
            throw new IllegalArgumentException(
                "Code must be a 6-digit OTP or a XXXX-XXXX-XXXX-XXXX backup code");
        }
    }

    /**
     * @return true if the code is a 6-digit TOTP OTP; false if it is a backup code
     */
    public boolean isOtpCode() {
        return code.matches(OTP_PATTERN);
    }
}

