package com.oodesigns.cas.domain.value;
import java.util.Objects;
import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Lightweight value object containing only the credentials needed for authentication.
 * Used to avoid loading the entire User object during password verification.
 * Validation happens in the static factory method before construction.
 * <p>
 * Contains:
 * - userId: identifier for the authenticated user
 * - passwordHash: the hashed password for verification
 */
public final class UserCredential extends ValidatedValue<UserCredential.CredentialData> {

    /**
     * Inner record to hold credential data.
     */
    public record CredentialData(UserId userId, PasswordHash passwordHash) {}

    /**
     * Create a user credential value object.
     * Assumes the values have already been validated.
     */
    private UserCredential(final UserId userId, final PasswordHash passwordHash) {
        super(new CredentialData(userId, passwordHash));
    }

    /**
     * Factory method to create a user credential.
     * Performs all validation before construction.
     *
     * @param userId the user identifier
     * @param passwordHash the password hash
     * @return UserCredential instance
     * @throws NullPointerException if userId or passwordHash is null
     */
    public static UserCredential of(final UserId userId, final PasswordHash passwordHash) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(passwordHash, "Password hash cannot be null");
        return new UserCredential(userId, passwordHash);
    }

    public UserId userId() {
        return value().userId();
    }

    public PasswordHash passwordHash() {
        return value().passwordHash();
    }
}

