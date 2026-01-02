package com.oodesigns.cas.infrastructure.config;

import com.oodesigns.cas.util.validation.ValidatedValue;

/** Typed, validated database port value. */
final class DatabasePort extends ValidatedValue<String, Integer> {

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65_535;

    DatabasePort(final String raw) {
        super(raw);
    }

    @Override
    protected Integer parse(final String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("db.port is missing");
        }
        final String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("db.port is blank");
        }
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("db.port must be a number: " + value, e);
        }
    }

    @Override
    protected Integer validate(final Integer value) {
        if (value < MIN_PORT || value > MAX_PORT) {
            throw new IllegalArgumentException("db.port must be between " + MIN_PORT + " and " + MAX_PORT + ": " + value);
        }
        return value;
    }
}
