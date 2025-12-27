package com.oodesigns.cas.domain.value;

import com.oodesigns.cas.domain.entity.User;

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

    /**
     * Extract credentials from a full User object.
     * 
     * @param user the user entity
     * @return UserCredential with userId and passwordHash
     */
    public static UserCredential from(final User user) {
        Objects.requireNonNull(user, "User cannot be null");
        return new UserCredential(user.userId(), user.passwordHash());
    }
}
