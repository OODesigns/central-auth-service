package com.oodesigns.cas.infrastructure.config;

import java.util.regex.Pattern;

import com.oodesigns.cas.util.validation.ValidatedValue;

/** Typed, validated database user value. */
final class DatabaseUser extends ValidatedValue<String> {

    private static final Pattern USER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_-]*$");

    private DatabaseUser(final String value) {
        super(value);
    }

    private static String parseAndValidate(final String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("db.user is missing");
        }
        final String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("db.user is blank");
        }
        if (!USER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(String.format("db.user contains invalid characters: %s", value));
        }
        return value;
    }

    static DatabaseUser of(final String raw) {
        return new DatabaseUser(parseAndValidate(raw));
    }
}
