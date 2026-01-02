package com.oodesigns.cas.domain.value;

import java.util.Objects;

/**
 * Credentials record bundling user credential and password for authentication.
 * Immutable domain value object representing user authentication context.
 * Validates that both credential and password are non-null.
 * Implements AutoCloseable to automatically clear the password when closed.
 * 
 * @param credential The user credential (userId + passwordHash) for verification (non-null)
 * @param password The user's password for verification (non-null)
 */
public record Credentials(UserCredential credential, Password password) implements AutoCloseable {
    
    /**
     * Compact constructor that validates all required fields are non-null.
     */
    public Credentials {
        Objects.requireNonNull(credential, "User credential is required for authentication");
        Objects.requireNonNull(password, "Password is required for authentication");
    }

    /**
     * Closes this resource and clears the sensitive password data.
     * This method is idempotent and safe to call multiple times.
     */
    @Override
    public void close() {
        if (password != null) {
            password.clear();
        }
    }
}
