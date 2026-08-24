package com.oodesigns.cas.domain.value;

import java.util.Objects;
import java.util.UUID;
import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Value object representing a user identifier.
 * Immutable and validated via factory method.
 */
public final class UserId extends ValidatedValue<UUID> {

    /**
     * Create a user ID value object.
     * Assumes the value has already been validated.
     *
     * @param value the validated UUID
     */
    private UserId(final UUID value) {
        super(value);
    }

    /**
     * Factory method to create a user ID from a UUID.
     * 
     * @param uuid the UUID to use as user ID
     * @return UserId instance
     * @throws NullPointerException if uuid is null
     */
    public static UserId of(final UUID uuid) {
        Objects.requireNonNull(uuid, "User ID UUID cannot be null");
        return new UserId(uuid);
    }

    /**
     * Factory method to create a user ID from a UUID string.
     * Performs all validation before construction.
     * 
     * @param value the UUID string
     * @return UserId instance
     * @throws NullPointerException if value is null
     * @throws IllegalArgumentException if value is not a valid UUID format
     */
    public static UserId of(final String value) {
        Objects.requireNonNull(value, "User ID string cannot be null");
        final UUID uuid = validateAndParseUuid(value);
        return new UserId(uuid);
    }

    /**
     * Validate and parse a UUID string.
     * 
     * @param value the UUID string to parse
     * @return the parsed UUID
     * @throws IllegalArgumentException if value is not a valid UUID format
     */
    private static UUID validateAndParseUuid(final String value) {
        try {
            return UUID.fromString(value);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Invalid user ID format: %s", value), e);
        }
    }

    /**
     * Get the underlying UUID value.
     * 
     * @return the UUID
     */
    public UUID asUUID() {
        return value();
    }
}

