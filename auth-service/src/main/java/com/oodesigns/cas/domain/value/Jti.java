package com.oodesigns.cas.domain.value;

import jakarta.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;
import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Value object representing a JWT ID (jti claim).
 * Used to track and revoke individual access tokens.
 * Validation happens in the static factory method before construction.
 */
public final class Jti extends ValidatedValue<UUID> {

    /**
     * Create a JTI value object.
     * Assumes the value has already been validated.
     *
     * @param value the validated UUID
     */
    private Jti(final UUID value) {
        super(value);
    }

    /**
     * Generate a random JTI.
     * 
     * @return a new Jti with a randomly generated UUID
     */
    public static Jti generate() {
        return new Jti(UUID.randomUUID());
    }

    /**
     * Factory method to create a JTI from a UUID.
     * 
     * @param uuid the UUID to use as JTI
     * @return Jti instance
     * @throws NullPointerException if uuid is null
     */
    public static Jti of(final UUID uuid) {
        Objects.requireNonNull(uuid, "JTI UUID cannot be null");
        return new Jti(uuid);
    }

    /**
     * Factory method to create a JTI from a UUID string.
     * Performs all validation before construction.
     * 
     * @param value the UUID string
     * @return Jti instance
     * @throws NullPointerException if value is null
     * @throws IllegalArgumentException if value is not a valid UUID format
     */
    public static Jti of(final String value) {
        Objects.requireNonNull(value, "JTI string cannot be null");
        final UUID uuid = validateAndParseUuid(value);
        return new Jti(uuid);
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
            throw new IllegalArgumentException(String.format("Invalid JTI format: %s", value), e);
        }
    }

    /**
     * Get the underlying UUID value.
     * 
     * @return the UUID
     */
    @Nonnull
    public UUID asUUID() {
        return value();
    }
}

