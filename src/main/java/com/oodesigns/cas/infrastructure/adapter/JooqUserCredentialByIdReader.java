package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.PasswordHash;
import com.oodesigns.cas.domain.value.UserCredential;
import com.oodesigns.cas.domain.value.UserId;
import org.jooq.DSLContext;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JOOQ-based implementation of {@link Ports.UserCredentialByIdRetriever}.
 * <p>
 * Looks up stored credentials by {@link UserId} (UUID primary key) via the
 * {@code api_schema.find_user_credentials_by_id(uuid)} SECURITY DEFINER function.
 * <p>
 * Used exclusively for re-authentication flows (e.g. disabling 2FA), where the
 * {@code userId} is sourced from a verified session/token rather than client input.
 * This avoids the TOCTOU risk of looking up credentials by a client-supplied username.
 */
public final class JooqUserCredentialByIdReader implements Ports.UserCredentialByIdRetriever {

    private final DSLContext dsl;

    /**
     * @param dsl jOOQ DSL context used to invoke the credential lookup function
     * @throws NullPointerException if {@code dsl} is null
     */
    public JooqUserCredentialByIdReader(final DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext cannot be null");
    }

    @Override
    public Optional<UserCredential> findCredentialsByUserId(final UserId userId) {
        return Optional.ofNullable(userId)
                .flatMap(uid -> Routines.findUserCredentialsById(dsl, uid.asUUID())
                        .map(r -> UserCredential.of(
                                UserId.of(r.userId()),
                                PasswordHash.of(r.passwordHash())
                        )));
    }

    /**
     * Hand-written JOOQ routine shim, following the same pattern as {@code UserCredentialReader}.
     */
    private static final class Routines {
        static Optional<UserCredentialsRecord> findUserCredentialsById(
                final DSLContext ctx, final UUID userId) {
            return ctx.fetchOptional(
                            "SELECT * FROM api_schema.find_user_credentials_by_id(?)", userId)
                    .map(r -> new UserCredentialsRecord(
                            r.get("user_id", UUID.class),
                            r.get("password_hash", String.class)
                    ));
        }
    }

    private record UserCredentialsRecord(UUID userId, String passwordHash) {}
}

