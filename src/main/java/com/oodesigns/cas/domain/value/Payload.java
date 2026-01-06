package com.oodesigns.cas.domain.value;
import java.util.Objects;
import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Represents a JSON payload to embed inside JWT tokens.
 * Ensures the payload is neither null nor blank before signing.
 */
public final class Payload extends ValidatedValue<String, String> {

    public Payload(final String value) {
        super(value);
    }

    @Override
    protected String parse(final String raw) {
        Objects.requireNonNull(raw, "Payload cannot be null");
        return raw.trim();
    }

    @Override
    protected String validate(final String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Payload cannot be blank");
        }
        return value;
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
        return new Payload(payload);
    }
}
