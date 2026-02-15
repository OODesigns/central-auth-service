package com.oodesigns.cas.domain.value;

import com.oodesigns.cas.util.validation.ValidatedValue;
import java.util.Objects;

/**
 * Value object representing a TOTP (Time-based One-Time Password) secret
 * for authenticator app-based 2FA.
 * <p>
 * Extends ValidatedValue to ensure immutability and proper validation
 * of the Base32-encoded secret key.
 * <p>
 * Validation Rules:
 * - Secret must not be null
 * - Secret must be non-empty
 * - Secret must be valid Base32 encoding (alphanumeric + padding)
 * - Minimum length: 16 characters (80 bits entropy)
 */
public final class SecretFor2FA extends ValidatedValue<String> {

    private SecretFor2FA(final String value) {
        super(value);
    }

    /**
     * Factory method to create a TotpSecret with validation.
     *
     * @param value Base32-encoded TOTP secret
     * @return TotpSecret instance
     * @throws NullPointerException if value is null
     * @throws IllegalArgumentException if value is invalid
     */
    public static SecretFor2FA of(final String value) {
        Objects.requireNonNull(value, "TOTP secret cannot be null");

        // Validate Base32 encoding
        if (!isValidBase32(value)) {
            throw new IllegalArgumentException("TOTP secret must be valid Base32 encoding");
        }

        // Validate minimum entropy (16 chars = 80 bits)
        if (value.length() < 16) {
            throw new IllegalArgumentException(
                "TOTP secret must be at least 16 characters (80 bits entropy)"
            );
        }

        return new SecretFor2FA(value);
    }

    /**
     * Validate Base32 encoding (RFC 4648).
     *
     * Valid characters: A-Z, 2-7, and optional '=' padding.
     *
     * @param value string to validate
     * @return true if valid Base32, false otherwise
     */
    private static boolean isValidBase32(final String value) {
        if (value.isEmpty()) {
            return false;
        }

        // Remove padding first
        final String base32WithoutPadding = value.replaceAll("=+$", "");

        // Check if all characters are valid Base32
        return base32WithoutPadding.matches("^[A-Z2-7]+$");
    }

    /**
     * Get the TOTP secret value (Base32-encoded).
     *
     * @return the secret key string
     */
    public String getSecret() {
        return value();
    }

    /**
     * Get the secret length (number of Base32 characters).
     *
     * @return length of secret
     */
    public int length() {
        return value().length();
    }
}

