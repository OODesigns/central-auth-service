package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import java.util.Objects;
import org.jooq.DSLContext;

/**
 * Factory for creating UserCredentialReader implementations.
 * Standardized on jOOQ for compile-time schema safety.
 * jOOQ provides critical guarantees for auth code:
 * - Column renames break at compile time, not runtime
 * - Function signature changes detected before deployment
 * - RLS/permission schema changes require code updates
 * - Refactor-safe across auth policy changes
 */
public final class UserCredentialReaderFactory {

    private UserCredentialReaderFactory() {
        // Factory class, not instantiable
    }

    /**
     * Creates a user credential reader with jOOQ type-safe queries.
     * Recommended for security-sensitive code with schema evolution.
     *
     * @param dslContext jOOQ DSL context for type-safe queries
     * @return A UserCredentialReader implementation
     * @throws NullPointerException if dslContext is null
     */
    public static Ports.UserCredentialReader jooq(final DSLContext dslContext) {
        Objects.requireNonNull(dslContext, "DSLContext cannot be null");
        return new JooqUserCredentialReader(dslContext);
    }
}


