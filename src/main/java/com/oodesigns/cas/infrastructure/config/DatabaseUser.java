package com.oodesigns.cas.infrastructure.config;

import java.util.regex.Pattern;

import com.oodesigns.cas.util.validation.ValidatedValue;

/** Typed, validated database user value. */
final class DatabaseUser extends ValidatedValue<String, String> {

    private static final Pattern USER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_-]*$");

    DatabaseUser(final String raw) {
        super(raw);
    }

    @Override
    protected String parse(final String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("db.user is missing");
        }
        final String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("db.user is blank");
        }
        return value;
    }

    @Override
    protected String validate(final String value) {
        if (!USER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("db.user contains invalid characters: " + value);
        }
        return value;
    }
}
