package com.oodesigns.cas.infrastructure.config;

import java.util.Arrays;

/** Typed, validated database password value backed by clearable memory. */
public final class DatabasePassword implements AutoCloseable {

    private static final int MIN_LENGTH = 8;
    private static final String SPECIAL_CHARACTERS = "!@#$%^&*()_+-=[]{};:'\",.<>?/\\|`~";
    private final char[] value;

    private DatabasePassword(final char[] value) {
        this.value = value;
    }

    static DatabasePassword of(final String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("db.password is missing");
        }

        final char[] value = raw.toCharArray();
        try {
            return of(value);
        } finally {
            Arrays.fill(value, '\0');
        }
    }

    public static DatabasePassword of(final char[] raw) {
        if (raw == null) {
            throw new IllegalArgumentException("db.password is missing");
        }

        final char[] value = trim(raw);
        boolean valid = false;
        try {
            if (value.length == 0) {
                throw new IllegalArgumentException("db.password is blank");
            }
            if (value.length < MIN_LENGTH) {
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
            valid = true;
            return new DatabasePassword(value);
        } finally {
            if (!valid) {
                Arrays.fill(value, '\0');
            }
        }
    }

    public char[] chars() {
        return value.clone();
    }

    public DatabasePassword copy() {
        return new DatabasePassword(chars());
    }

    @Override
    public void close() {
        Arrays.fill(value, '\0');
    }

    @Override
    public String toString() {
        return "DatabasePassword{***}";
    }

    private static char[] trim(final char[] raw) {
        int start = 0;
        int end = raw.length;
        while (start < end && raw[start] <= ' ') {
            start++;
        }
        while (end > start && raw[end - 1] <= ' ') {
            end--;
        }
        return Arrays.copyOfRange(raw, start, end);
    }

    private static boolean hasUppercase(final char[] value) {
        for (final char character : value) {
            if (character >= 'A' && character <= 'Z') {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDigit(final char[] value) {
        for (final char character : value) {
            if (character >= '0' && character <= '9') {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSpecialChar(final char[] value) {
        for (final char character : value) {
            if (SPECIAL_CHARACTERS.indexOf(character) >= 0) {
                return true;
            }
        }
        return false;
    }
}
