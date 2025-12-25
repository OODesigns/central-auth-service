package com.oodesigns.cas.domain.value;

import java.util.Objects;

/**
 * Represents a JSON payload to embed inside JWT tokens.
 * Ensures the payload is neither null nor blank before signing.
 */
public final class Payload {
    private final String value;

    private Payload(final String value) {
        this.value = value;
    }

    /**
     * Factory method to create a validated payload.
     *
     * @param payload raw payload string
     * @return Payload instance containing the provided value
     * @throws NullPointerException if payload is null
     * @throws IllegalArgumentException if payload is blank
     */
    public static Payload of(final String payload) {
        final String nonNull = Objects.requireNonNull(payload, "Payload cannot be null");
        if (nonNull.isBlank()) {
            throw new IllegalArgumentException("Payload cannot be empty");
        }
        return new Payload(nonNull);
    }

    /**
     * Access the validated payload value.
     *
     * @return underlying payload string
     */
    public String value() {
        return value;
    }
}
