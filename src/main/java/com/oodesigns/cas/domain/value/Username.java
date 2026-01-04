package com.oodesigns.cas.domain.value;
import java.util.regex.Pattern;
import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Value object representing a username.
 * Validates format at construction.
 */
public final class Username extends ValidatedValue<String, String> {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,50}$");

    public Username(final String value) {
        super(value);
    }

    public static Username of(final String value) {
        return new Username(value);
    }

    @Override
    protected String parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }
        return raw.toLowerCase();
    }

    @Override
    protected String validate(final String value) {
        if (!USERNAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Username must be 3-50 characters, alphanumeric with _/-");
        }
        return value;
    }
}
