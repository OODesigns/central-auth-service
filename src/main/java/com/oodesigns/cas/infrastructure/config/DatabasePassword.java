package com.oodesigns.cas.infrastructure.config;

import com.oodesigns.cas.util.validation.ValidatedValue;

/** Typed, validated database password value. */
final class DatabasePassword extends ValidatedValue<String> {

    private static final int MIN_LENGTH = 8;

    private DatabasePassword(final String value) {
        super(value);
    }

    private static String parseAndValidate(final String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("db.password is missing");
        }
        final String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("db.password is blank");
        }
        if (value.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(String.format("db.password must be at least %d characters", MIN_LENGTH));
        }
        if (!hasUppercase(value)) {
            throw new IllegalArgumentException("db.password must contain at least one uppercase letter");
        }
        if (!hasDigit(value)) {
            throw new IllegalArgumentException("db.password must contain at least one digit");
        }
        if (!hasSpecialChar(value)) {
            throw new IllegalArgumentException("db.password must contain at least one special character");
        }
        return value;
    }

    static DatabasePassword of(final String raw) {
        return new DatabasePassword(parseAndValidate(raw));
    }

    private static boolean hasUppercase(final String value) {
        return value.matches(".*[A-Z].*");
    }

    private static boolean hasDigit(final String value) {
        return value.matches(".*\\d.*");
    }

    private static boolean hasSpecialChar(final String value) {
        return value.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};:'\",.<>?/\\\\|`~].*");
    }
}
