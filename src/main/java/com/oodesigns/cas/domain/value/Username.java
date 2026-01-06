package com.oodesigns.cas.domain.value;
import java.util.Objects;
import java.util.regex.Pattern;
import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Value object representing a username.
 * Validates format via factory method.
 */
public final class Username extends ValidatedValue<String> {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,50}$");

    /**
     * Create a username value object.
     * Assumes the value has already been validated.
     *
     * @param value the validated username string
     */
    private Username(final String value) {
        super(value);
    }

    /**
     * Factory method to create a username.
     * Performs all validation before construction.
     * 
     * @param value the username string
     * @return Username instance
     * @throws NullPointerException if value is null
     * @throws IllegalArgumentException if value is blank or invalid format
     */
    public static Username of(final String value) {
        Objects.requireNonNull(value, "Username cannot be null");
        validateUsername(value);  // Perform validation
        return new Username(value.toLowerCase());  // Normalize to lowercase
    }

    /**
     * Validate that the given string is a valid username.
     * 
     * @param value the username to validate
     * @throws IllegalArgumentException if invalid
     */
    private static void validateUsername(final String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
        if (!USERNAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Username must be 3-50 characters, alphanumeric with _/-");
        }
    }
}
