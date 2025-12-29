package com.oodesigns.cas.domain.value;
import java.util.Objects;

/**
 * Lightweight value object containing only the credentials needed for authentication.
 * Used to avoid loading the entire User object during password verification.
 * 
 * Contains:
 * - userId: identifier for the authenticated user
 * - passwordHash: the hashed password for verification
 */
public record UserCredential(UserId userId, PasswordHash passwordHash) {
    public UserCredential {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(passwordHash, "Password hash cannot be null");
    }
}