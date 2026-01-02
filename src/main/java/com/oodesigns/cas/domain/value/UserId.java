package com.oodesigns.cas.domain.value;

import jakarta.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a user identifier.
 * Immutable and validated at construction.
 */
public record UserId(UUID value) {
    public UserId {
        Objects.requireNonNull(value, "User ID cannot be null");
    }

    public static UserId of(final String value) {
        Objects.requireNonNull(value, "User ID string cannot be null");
        try {
            return new UserId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Invalid user ID format: %s", value), e);
        }
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    @Nonnull
    public UUID asUUID() {
        return value;
    }

    @Nonnull
    public String asString() {
        return value.toString();
    }

    @Nonnull
    @Override
    public String toString() {
        return value.toString();
    }
}
