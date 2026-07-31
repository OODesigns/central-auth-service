package com.oodesigns.cas.infrastructure.config;

import java.util.regex.Pattern;

import com.oodesigns.cas.util.validation.ValidatedValue;

/** Typed, validated database host value. */
final class DatabaseHost extends ValidatedValue<String> {

    private static final Pattern HOST_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9.-]*[a-zA-Z0-9]$");

    private DatabaseHost(final String value) {
        super(value);
    }

    private static String parseAndValidate(final String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("db.host is missing");
        }
        final String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("db.host is blank");
        }
        if (!HOST_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(String.format("db.host contains invalid characters: %s", value));
        }
        if (value.contains("..")) {
            throw new IllegalArgumentException(String.format("db.host cannot contain consecutive dots: %s", value));
        }
        return value;
    }

    static DatabaseHost of(final String raw) {
        return new DatabaseHost(parseAndValidate(raw));
    }
}
