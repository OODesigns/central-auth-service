package com.oodesigns.cas.domain.value;
import java.util.Objects;
import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Value object representing a fine-grained permission.
 * Permissions are loaded from the database (permissions table) at runtime.
 * This class represents those permissions in the domain model.
 * Database examples: create_user, update_user, delete_user, view_audit_log, etc.
 * The static factory methods are convenience shortcuts for common permissions,
 * but the primary pattern is to load permissions from the database via repositories.
 * Validation happens in the static factory method before construction.
 */
public final class Permission extends ValidatedValue<String> {

    /**
     * Create a permission value object.
     * Assumes the value has already been validated.
     *
     * @param value the validated permission string
     */
    private Permission(final String value) {
        super(value);
    }

    /**
     * Factory method to create a permission.
     * Performs all validation before construction.
     * 
     * @param value the permission string
     * @return Permission instance
     * @throws NullPointerException if value is null
     * @throws IllegalArgumentException if value is blank or invalid format
     */
    public static Permission of(final String value) {
        Objects.requireNonNull(value, "Permission cannot be null");
        validatePermission(value);  // Perform validation
        return new Permission(value);
    }

    /**
     * Validate that the given string is a valid permission.
     * Validates format: lowercase alphanumeric with underscores.
     * 
     * @param value the permission to validate
     * @throws IllegalArgumentException if invalid
     */
    private static void validatePermission(final String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Permission cannot be blank");
        }
        if (!value.matches("^[a-z0-9_]+$")) {
            throw new IllegalArgumentException("Permission must be lowercase alphanumeric with underscores");
        }
    }
}
