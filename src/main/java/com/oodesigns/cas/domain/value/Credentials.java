package com.oodesigns.cas.domain.value;

import java.util.Objects;
import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Credentials value object bundling user credential and password for authentication.
 * Immutable domain value object representing user authentication context.
 * Validates that both credential and password are non-null.
 * Implements AutoCloseable to automatically clear the password when closed.
 */
public final class Credentials extends ValidatedValue<Credentials.CredentialsData, Credentials.CredentialsData> implements AutoCloseable {

    /**
     * Inner record to hold the credential data.
     */
    public record CredentialsData(UserCredential credential, Password password) {}

    /**
     * Create credentials from a credential and password.
     */
    public Credentials(final UserCredential credential, final Password password) {
        super(new CredentialsData(credential, password));
    }

    @Override
    protected CredentialsData parse(final CredentialsData raw) {
        return raw;
    }

    @Override
    protected CredentialsData validate(final CredentialsData data) {
        Objects.requireNonNull(data.credential(), "User credential is required for authentication");
        Objects.requireNonNull(data.password(), "Password is required for authentication");
        return data;
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
        password().clear();
    }
}


