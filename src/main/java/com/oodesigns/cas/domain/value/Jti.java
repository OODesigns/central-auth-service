package com.oodesigns.cas.domain.value;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a JWT ID (jti claim).
 * Used to track and revoke individual access tokens.
 */
public record Jti(UUID value) {
    public Jti {
        Objects.requireNonNull(value, "JTI cannot be null");
    }

    public static Jti generate() {
        return new Jti(UUID.randomUUID());
    }

    public static Jti of(final String value) {
        Objects.requireNonNull(value, "JTI string cannot be null");
        try {
            return new Jti(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid JTI format: " + value, e);
        }
    }

    public UUID asUUID() {
        return value;
    }

    public String asString() {
        return value.toString();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
