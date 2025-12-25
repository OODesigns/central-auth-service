package com.oodesigns.cas.domain.value;

import com.oodesigns.cas.domain.entity.User;
import java.util.Objects;

/**
 * Credentials record bundling user and password for authentication.
 * Immutable domain value object representing authenticated user context.
 * Validates that both user and password are non-null.
 * Implements AutoCloseable to automatically clear the password when closed.
 * 
 * @param user The authenticated user (non-null)
 * @param password The user's password for verification (non-null)
 * @throws NullPointerException if user or password is null
 */
public record Credentials(User user, Password password) implements AutoCloseable {
    
    /**
     * Compact constructor that validates all required fields are non-null.
     */
    public Credentials {
        Objects.requireNonNull(user, "User is required for authentication");
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
