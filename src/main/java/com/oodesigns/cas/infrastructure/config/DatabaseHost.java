package com.oodesigns.cas.infrastructure.config;

import java.util.regex.Pattern;

import com.oodesigns.cas.util.validation.ValidatedValue;

/** Typed, validated database host value. */
final class DatabaseHost extends ValidatedValue<String, String> {

    private static final Pattern HOST_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9.-]*[a-zA-Z0-9]$");

    DatabaseHost(final String raw) {
        super(raw);
    }

    @Override
    protected String parse(final String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("db.host is missing");
        }
        final String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("db.host is blank");
        }
        return value;
    }

    @Override
    protected String validate(final String value) {
        if (!HOST_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("db.host contains invalid characters: " + value);
        }
        if (value.contains("..")) {
            throw new IllegalArgumentException("db.host cannot contain consecutive dots: " + value);
        }
        return value;
    }
}
