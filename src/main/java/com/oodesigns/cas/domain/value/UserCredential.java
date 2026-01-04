package com.oodesigns.cas.domain.value;
import java.util.Objects;
import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Lightweight value object containing only the credentials needed for authentication.
 * Used to avoid loading the entire User object during password verification.
 * 
 * Contains:
 * - userId: identifier for the authenticated user
 * - passwordHash: the hashed password for verification
 */
public final class UserCredential extends ValidatedValue<UserCredential.CredentialData, UserCredential.CredentialData> {

    /**
     * Inner record to hold credential data.
     */
    public record CredentialData(UserId userId, PasswordHash passwordHash) {}

    public UserCredential(final UserId userId, final PasswordHash passwordHash) {
        super(new CredentialData(userId, passwordHash));
    }

    @Override
    protected CredentialData parse(final CredentialData raw) {
        return raw;
    }

    @Override
    protected CredentialData validate(final CredentialData data) {
        Objects.requireNonNull(data.userId(), "User ID cannot be null");
        Objects.requireNonNull(data.passwordHash(), "Password hash cannot be null");
        return data;
    }

    public UserId userId() {
        return value().userId();
    }

    public PasswordHash passwordHash() {
        return value().passwordHash();
    }
}

