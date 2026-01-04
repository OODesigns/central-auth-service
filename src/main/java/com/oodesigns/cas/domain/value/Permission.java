package com.oodesigns.cas.domain.value;
import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Value object representing a fine-grained permission.
 * Permissions are loaded from the database (permissions table) at runtime.
 * This class represents those permissions in the domain model.
 * Database examples: create_user, update_user, delete_user, view_audit_log, etc.
 * The static factory methods are convenience shortcuts for common permissions,
 * but the primary pattern is to load permissions from the database via repositories.
 */
public final class Permission extends ValidatedValue<String, String> {

    public Permission(final String value) {
        super(value);
    }

    public static Permission of(final String value) {
        return new Permission(value);
    }

    @Override
    protected String parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Permission cannot be null or blank");
        }
        return raw;
    }

    @Override
    protected String validate(final String value) {
        // Validate format: lowercase alphanumeric with underscores
        if (!value.matches("^[a-z0-9_]+$")) {
            throw new IllegalArgumentException("Permission must be lowercase alphanumeric with underscores");
        }
        return value;
    }
}
