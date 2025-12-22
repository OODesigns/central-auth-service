package com.oodesigns.cas.domain.value;
import java.util.regex.Pattern;

/**
 * Value object representing a username.
 * Validates format at construction.
 */
public record Username(String value) {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,50}$");

    public Username {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }
        if (!USERNAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Username must be 3-50 characters, alphanumeric with _/-");
        }
        value = value.toLowerCase();
    }

    public static Username of(final String value) {
        return new Username(value);
    }

    public String asString() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
