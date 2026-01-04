package com.oodesigns.cas.domain.value;

import jakarta.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;
import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Value object representing a user identifier.
 * Immutable and validated at construction.
 */
public final class UserId extends ValidatedValue<UUID, UUID> {

    public UserId(final UUID value) {
        super(value);
    }

    @Override
    protected UUID parse(final UUID raw) {
        return raw;
    }

    @Override
    protected UUID validate(final UUID value) {
        Objects.requireNonNull(value, "User ID cannot be null");
        return value;
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
        return value();
    }
}

