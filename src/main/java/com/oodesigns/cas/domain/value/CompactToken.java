package com.oodesigns.cas.domain.value;

import com.oodesigns.cas.util.validation.ValidatedValue;

import java.util.Objects;

/** Validated compact JWT carried across internal application boundaries. */
public abstract class CompactToken extends ValidatedValue<String> {
    protected CompactToken(final String value) {
        super(validate(value));
    }

    private static String validate(final String value) {
        Objects.requireNonNull(value, "Token is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Token cannot be blank");
        }
        final String[] segments = value.split("\\.", -1);
        if (segments.length != 3 || java.util.Arrays.stream(segments).anyMatch(segment -> segment.isBlank())) {
            throw new IllegalArgumentException("Token must be a compact JWT");
        }
        return value;
    }

    @Override
    protected String getDisplayValue() {
        return getClass().getSimpleName() + "{***}";
    }
}
