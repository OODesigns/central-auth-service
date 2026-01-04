package com.oodesigns.cas.domain.value;

import jakarta.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;
import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Value object representing a JWT ID (jti claim).
 * Used to track and revoke individual access tokens.
 */
public final class Jti extends ValidatedValue<UUID, UUID> {

    public Jti(final UUID value) {
        super(value);
    }

    @Override
    protected UUID parse(final UUID raw) {
        return raw;
    }

    @Override
    protected UUID validate(final UUID value) {
        Objects.requireNonNull(value, "JTI cannot be null");
        return value;
    }

    public static Jti generate() {
        return new Jti(UUID.randomUUID());
    }

    public static Jti of(final String value) {
        Objects.requireNonNull(value, "JTI string cannot be null");
        try {
            return new Jti(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Invalid JTI format: %s", value), e);
        }
    }

    @Nonnull
    public UUID asUUID() {
        return value();
    }
}

