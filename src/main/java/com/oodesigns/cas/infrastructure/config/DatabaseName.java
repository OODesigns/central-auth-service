package com.oodesigns.cas.infrastructure.config;

import java.util.regex.Pattern;

import com.oodesigns.cas.util.validation.ValidatedValue;

/** Typed, validated database name value. */
final class DatabaseName extends ValidatedValue<String> {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_-]*$");

    private DatabaseName(final String value) {
        super(value);
    }

    private static String parseAndValidate(final String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("db.name is missing");
        }
        final String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("db.name is blank");
        }
        if (!NAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(String.format("db.name contains invalid characters: %s", value));
        }
        return value;
    }

    static DatabaseName of(final String raw) {
        return new DatabaseName(parseAndValidate(raw));
    }
}
