package com.oodesigns.cas.domain.value;

import com.oodesigns.cas.util.validation.ValidatedValue;
import java.util.Objects;

/**
 * Value object representing a backup code for TOTP 2FA account recovery.
 * <p>
 * Extends ValidatedValue to ensure immutability and proper validation
 * of the backup code format.
 * <p>
 * Backup codes are:
 * - Single-use recovery codes for account recovery when authenticator is lost
 * - Hashed before storage (never stored plaintext)
 * - Generated in batches (typically 10-16 codes per user)
 * - Alphanumeric with dashes for readability: XXXX-XXXX-XXXX-XXXX
 * <p>
 * Validation Rules:
 * - Code must not be null
 * - Code must be non-empty
 * - Code must be 19 characters (16 alphanumeric + 3 dashes)
 * - Code must match pattern: XXXX-XXXX-XXXX-XXXX
 */
public final class BackupCode extends ValidatedValue<String> {

    private BackupCode(final String value) {
        super(value);
    }

    /**
     * Factory method to create a BackupCode with validation.
     *
     * @param value the plaintext backup code in format XXXX-XXXX-XXXX-XXXX
     * @return BackupCode instance
     * @throws NullPointerException if value is null
     * @throws IllegalArgumentException if value is invalid
     */
    public static BackupCode of(final String value) {
        Objects.requireNonNull(value, "Backup code cannot be null");

        // Validate format: XXXX-XXXX-XXXX-XXXX (19 characters)
        if (!value.matches("^[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}$")) {
            throw new IllegalArgumentException(
                "Backup code must be in format XXXX-XXXX-XXXX-XXXX (alphanumeric with dashes)"
            );
        }

        return new BackupCode(value);
    }

    /**
     * Get the plaintext backup code.
     * <p>
     * IMPORTANT: This should only be called during code generation/display.
     * Never log or transmit the plaintext code except during initial display.
     *
     * @return the plaintext backup code
     */
    public String getCode() {
        return value();
    }

    /**
     * Get the code without dashes (for hashing).
     * <p>
     * Useful for preparing code for hashing before storage.
     *
     * @return code without formatting dashes
     */
    public String normalized() {
        return value().replace("-", "");
    }

    /**
     * Get the code length including dashes.
     *
     * @return 19 (XXXX-XXXX-XXXX-XXXX)
     */
    public int length() {
        return value().length();
    }
}

