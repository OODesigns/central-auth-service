package com.oodesigns.cas.domain.value;
import java.util.Objects;
import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Value object representing a hashed password.
 * Stores only the hash; never the plaintext.
 * Validation happens in the static factory method before construction.
 */
public final class PasswordHash extends ValidatedValue<String> {

    /**
     * Create a password hash value object.
     * Assumes the value has already been validated.
     *
     * @param value the validated bcrypt password hash
     */
    private PasswordHash(final String value) {
        super(value);
    }

    /**
     * Factory method to create a validated password hash.
     * Performs all validation before construction.
     *
     * @param hash the bcrypt password hash
     * @return PasswordHash instance containing the provided value
     * @throws NullPointerException if hash is null
     * @throws IllegalArgumentException if hash is blank or not in valid bcrypt format
     */
    public static PasswordHash of(final String hash) {
        Objects.requireNonNull(hash, "Password hash cannot be null");
        validatePasswordHash(hash);  // Perform validation
        return new PasswordHash(hash);
    }

    /**
     * Validate that the given string is a valid bcrypt password hash.
     * 
     * @param hash the password hash to validate
     * @throws IllegalArgumentException if invalid
     */
    private static void validatePasswordHash(final String hash) {
        if (hash.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be blank");
        }
        // Validate bcrypt format (rough check: $2a$, $2b$, or $2y$ prefix)
        if (!hash.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$")) {
            throw new IllegalArgumentException("Password hash must be in bcrypt format");
        }
    }

    @Override
    protected String getDisplayValue() {
        return "PasswordHash(****)";
    }
}
