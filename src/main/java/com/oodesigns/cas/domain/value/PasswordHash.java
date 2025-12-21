package com.oodesigns.cas.domain.value;

import java.util.Objects;

/**
 * Value object representing a hashed password.
 * Stores only the hash; never the plaintext.
 */
public final class PasswordHash {
    private final String value;

    public PasswordHash(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be null or blank");
        }
        // Validate bcrypt format (rough check: $2a$, $2b$, or $2y$ prefix)
        if (!value.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$")) {
            throw new IllegalArgumentException("Password hash must be in bcrypt format");
        }
        this.value = value;
    }

    public String asString() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof PasswordHash)) return false;
        PasswordHash that = (PasswordHash) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "PasswordHash(****)";
    }
}
