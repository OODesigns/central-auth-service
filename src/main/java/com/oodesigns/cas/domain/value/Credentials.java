package com.oodesigns.cas.domain.value;

import java.util.Objects;
import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Credentials value object bundling user credential and password for authentication.
 * Immutable domain value object representing user authentication context.
 * Validates that both credential and password are non-null via factory method.
 * Implements AutoCloseable to automatically clear the password when closed.
 */
public final class Credentials extends ValidatedValue<Credentials.CredentialsData> implements AutoCloseable {

    /**
     * Inner record to hold the credential data.
     */
    public record CredentialsData(UserCredential credential, Password password) {}

    /**
     * Create credentials from a credential and password.
     * Assumes the values have already been validated.
     */
    private Credentials(final UserCredential credential, final Password password) {
        super(new CredentialsData(credential, password));
    }

    /**
     * Factory method to create credentials.
     * Performs all validation before construction.
     *
     * @param credential the user credential
     * @param password the password
     * @return Credentials instance
     * @throws NullPointerException if credential or password is null
     */
    public static Credentials of(final UserCredential credential, final Password password) {
        Objects.requireNonNull(credential, "User credential is required for authentication");
        Objects.requireNonNull(password, "Password is required for authentication");
        return new Credentials(credential, password);
    }

    public UserCredential credential() {
        return value().credential();
    }

    public Password password() {
        return value().password();
    }

    /**
     * Closes this resource and clears the sensitive password data.
     * This method is idempotent and safe to call multiple times.
     */
    @Override
    public void close() {
                password().close();
    }
}


