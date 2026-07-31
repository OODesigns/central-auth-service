package com.oodesigns.cas.domain.value;
import java.util.Objects;
import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Represents a JSON payload to embed inside JWT tokens.
 * Ensures the payload is neither null nor blank before signing.
 * Validation happens in the static factory method before construction.
 */
public final class Payload extends ValidatedValue<String> {

    /**
     * Create a payload value object.
     * Assumes the value has already been validated.
     *
     * @param value the validated payload string
     */
    private Payload(final String value) {
        super(value);
    }

    /**
     * Factory method to create a validated payload.
     * Performs all validation before construction.
     *
     * @param payload raw payload string
     * @return Payload instance containing the provided value
     * @throws NullPointerException if payload is null
     * @throws IllegalArgumentException if payload is blank
     */
    public static Payload of(final String payload) {
        Objects.requireNonNull(payload, "Payload cannot be null");
        validatePayload(payload);  // Perform validation
        return new Payload(payload);
    }

    /**
     * Validate that the given payload is not blank.
     * 
     * @param payload the payload to validate
     * @throws IllegalArgumentException if invalid
     */
    private static void validatePayload(final String payload) {
        if (payload.isBlank()) {
            throw new IllegalArgumentException("Payload cannot be blank");
        }
    }
}
