package com.oodesigns.cas.domain.value;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a JWT ID (jti claim).
 * Used to track and revoke individual access tokens.
 */
public final class Jti {
    private final UUID value;

    public Jti(final UUID value) {
        this.value = Objects.requireNonNull(value, "JTI cannot be null");
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
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Jti)) return false;
        Jti jti = (Jti) o;
        return Objects.equals(value, jti.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
