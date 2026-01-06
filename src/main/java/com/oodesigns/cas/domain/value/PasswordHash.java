package com.oodesigns.cas.domain.value;
import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Value object representing a hashed password.
 * Stores only the hash; never the plaintext.
 */
public final class PasswordHash extends ValidatedValue<String, String> {

    public PasswordHash(final String value) {
        super(value);
    }

    @Override
    protected String parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be null or blank");
        }
        return raw;
    }

    @Override
    protected String validate(final String value) {
        // Validate bcrypt format (rough check: $2a$, $2b$, or $2y$ prefix)
        if (!value.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$")) {
            throw new IllegalArgumentException("Password hash must be in bcrypt format");
        }
        return value;
    }

    @Override
    protected String getDisplayValue() {
        return "PasswordHash(****)";
    }

    /**
     * Factory method to create a validated password hash.
     *
     * @param hash the bcrypt password hash
     * @return PasswordHash instance containing the provided value
     * @throws IllegalArgumentException if hash is null, blank, or not in valid bcrypt format
     */
    public static PasswordHash of(final String hash) {
        return new PasswordHash(hash);
    }
}
