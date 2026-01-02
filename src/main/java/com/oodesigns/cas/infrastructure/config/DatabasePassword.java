package com.oodesigns.cas.infrastructure.config;

import com.oodesigns.cas.util.validation.ValidatedValue;

/** Typed, validated database password value. */
final class DatabasePassword extends ValidatedValue<String, String> {

    private static final int MIN_LENGTH = 8;

    DatabasePassword(final String raw) {
        super(raw);
    }

    @Override
    protected String parse(final String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("db.password is missing");
        }
        final String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("db.password is blank");
        }
        return value;
    }

    @Override
    protected String validate(final String value) {
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
