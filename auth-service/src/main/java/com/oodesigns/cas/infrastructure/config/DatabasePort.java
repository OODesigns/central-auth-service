package com.oodesigns.cas.infrastructure.config;

import com.oodesigns.cas.util.validation.ValidatedValue;

/** Typed, validated database port value. */
final class DatabasePort extends ValidatedValue<Integer> {

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65_535;

    private DatabasePort(final Integer value) {
        super(value);
    }

    private static Integer parseAndValidate(final String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("db.port is missing");
        }
        final String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("db.port is blank");
        }
        try {
            final int port = Integer.parseInt(value);
            if (port < MIN_PORT || port > MAX_PORT) {
                throw new IllegalArgumentException(String.format("db.port must be between %d and %d: %s", MIN_PORT, MAX_PORT, port));
            }
            return port;
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(String.format("db.port must be a number: %s", value), e);
        }
    }

    static DatabasePort of(final String raw) {
        return new DatabasePort(parseAndValidate(raw));
    }
}
