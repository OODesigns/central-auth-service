package com.oodesigns.cas.domain.value;

import jakarta.annotation.Nonnull;

/**
 * Value object representing a fine-grained permission.
 * 
 * Permissions are loaded from the database (permissions table) at runtime.
 * This class represents those permissions in the domain model.
 * 
 * Database examples: create_user, update_user, delete_user, view_audit_log, etc.
 * 
 * The static factory methods are convenience shortcuts for common permissions,
 * but the primary pattern is to load permissions from the database via repositories.
 */
public record Permission(String value) {
    public Permission {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Permission cannot be null or blank");
        }
        // Validate format: lowercase alphanumeric with underscores
        if (!value.matches("^[a-z0-9_]+$")) {
            throw new IllegalArgumentException("Permission must be lowercase alphanumeric with underscores");
        }
    }

    /**
     * Factory method to create a Permission from a database value.
     * The permission name should match a row in the permissions table.
     * 
     * @param value the permission name from the database
     * @return new Permission instance
     */
    public static Permission of(final String value) {
        return new Permission(value);
    }

    /**
     * Convert Permission to its string value for serialization.
     * 
     * @return the permission name (e.g., "create_user")
     */
    @Nonnull
    public String asString() {
        return value;
    }

    @Nonnull
    @Override
    public String toString() {
        return value;
    }
}
