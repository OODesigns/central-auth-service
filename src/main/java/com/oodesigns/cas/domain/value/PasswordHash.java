package com.oodesigns.cas.domain.value;

/**
 * Value object representing a hashed password.
 * Stores only the hash; never the plaintext.
 */
public record PasswordHash(String value) {
    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be null or blank");
        }
        // Validate bcrypt format (rough check: $2a$, $2b$, or $2y$ prefix)
        if (!value.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$")) {
            throw new IllegalArgumentException("Password hash must be in bcrypt format");
        }
    }

    public String asString() {
        return value;
    }

    @Override
    public String toString() {
        return "PasswordHash(****)";
    }
}
