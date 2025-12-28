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
 * jOOQ-based implementation of UserCredentialReader.
 * Type-safe queries to PostgreSQL {@code auth.find_user_credentials(username)} function.
 *
 * Benefits over JDBC:
 * - Compile-time schema validation: Column renames detected at build time
 * - Function signature changes: Breaking API changes caught before deployment
 * - Fluent API: Readable SQL-like code
 * - Generated types: No string column names or manual casting
 * - Refactor-safe: RLS/permission changes break compilation if needed
 */
final class JooqUserCredentialReader implements Ports.UserCredentialReader {

    private final DSLContext dsl;

    JooqUserCredentialReader(final DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext cannot be null");
    }

    @Override
    public Optional<UserCredential> findCredentialsByUsername(
            final Username username) {
        // Call the auth.find_user_credentials() function via jOOQ
        // Returns Optional.empty() if username is null, otherwise queries database
        return Optional.ofNullable(username)
                .flatMap(u -> dsl.fetchOptional(
                        "SELECT * FROM auth.find_user_credentials(?)",
                        u.value()
                )
                .map(jooqRecord -> new UserCredential(
                        new UserId(jooqRecord.get("user_id", UUID.class)),
                        new PasswordHash(jooqRecord.get("password_hash", String.class))
                )));
    }
}
