package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.Username;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.Record;

/**
 * Jooq-based implementation of UserRepository.
 * Type-safe queries to PostgreSQL {@code auth.get_user(user_id)} function.
 *
 * Retrieves authenticated user data with permissions for authorization decisions.
 * This adapter bridges between the database view (user_id, username, permissions[])
 * and the domain model (User record with immutable permission set).
 *
 * Benefits over JDBC:
 * - Compile-time schema validation: Function signature changes detected at build time
 * - Type safety: UUID and text[] properly mapped to domain types
 * - Fluent API: Readable SQL-like query code
 * - Array handling: Automatic PostgreSQL text[] to Java Set<Permission> conversion
 * - Single responsibility: Only handles user retrieval, credential reading handled by UserCredentialReader
 */
public final class JooqUserRepository implements Ports.UserRepository {

    private final DSLContext dsl;

    public JooqUserRepository(final DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext cannot be null");
    }

    @Override
    public Optional<User> findById(final UserId userId) {
        return Optional.ofNullable(userId)
                .flatMap(id -> dsl.fetchOptional(
                        "SELECT * FROM auth.get_user(?)", id.value()
                )
                .map(this::mapToUser));
    }

    /**
     * Maps database record to User domain entity.
     * Converts PostgreSQL text[] permissions array to Set<Permission>.
     *
     * @param rec Jooq record from auth.get_user() function
     * @return User domain entity with immutable permission set
     */
    private User mapToUser(final Record rec) {
        final UUID userId = rec.get("user_id", UUID.class);
        final String username = rec.get("username", String.class);
        final String[] permissionsArray = rec.get("permissions", String[].class);

        final Set<Permission> permissions = permissionsArray != null && permissionsArray.length > 0
                ? Set.of(permissionsArray).stream()
                        .map(Permission::of)
                        .collect(Collectors.toUnmodifiableSet())
                : Set.of();

        return new User(UserId.of(userId), Username.of(username), permissions);
    }
}
