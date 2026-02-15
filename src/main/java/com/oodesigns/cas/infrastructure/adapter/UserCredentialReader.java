package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.PasswordHash;
import com.oodesigns.cas.domain.value.UserCredential;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.Username;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
/**
 * Jooq-based implementation of UserCredentialReader.
 * <p>
 * Uses manually simulated type-safe record classes (Routines, UserCredentialsRecord)
 * that mirror jOOQ's generated code structure. This provides compile-time type safety
 * while remaining compatible with future jOOQ code generation.
 * </p>
 */
public final class UserCredentialReader implements Ports.UserCredentialRetriever {

    private final DSLContext dsl;

    public UserCredentialReader(final DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext cannot be null");
    }

    @Override
    public Optional<UserCredential> findCredentialsByUsername(final Username username) {
        return Optional.ofNullable(username)
                .flatMap(u -> Routines.findUserCredentials(dsl, u.value())
                        .map(r -> UserCredential.of(
                                UserId.of(r.userId()),
                                PasswordHash.of(r.passwordHash())
                        )));
    }

    /**
     * Manually simulated "Generated" classes to provide type safety and encapsulation.
     * This mimics the structure of jOOQ's generated code.
     */
    private static final class Routines {
        static Optional<UserCredentialsRecord> findUserCredentials(final DSLContext ctx, final String username) {
            return ctx.fetchOptional("SELECT * FROM api_schema.find_user_credentials(?)", username)
                    .map(r -> new UserCredentialsRecord(
                            r.get("user_id", UUID.class),
                            r.get("password_hash", String.class)
                    ));
        }
    }

    private record UserCredentialsRecord(UUID userId, String passwordHash) { }
}
