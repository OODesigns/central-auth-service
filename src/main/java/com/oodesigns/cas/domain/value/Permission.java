package com.oodesigns.cas.domain.value;

/**
 * Value object representing a fine-grained permission.
 * Examples: view_users, edit_profile, delete_accounts, approve_transfers
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

    public static Permission of(final String value) {
        return new Permission(value);
    }

    // Standard permissions for common operations
    public static Permission VIEW_USERS() {
        return new Permission("view_users");
    }

    public static Permission EDIT_PROFILE() {
        return new Permission("edit_profile");
    }

    public static Permission VIEW_REPORTS() {
        return new Permission("view_reports");
    }

    public static Permission MANAGE_USERS() {
        return new Permission("manage_users");
    }

    public static Permission DELETE_ACCOUNTS() {
        return new Permission("delete_accounts");
    }

    public static Permission APPROVE_TRANSFERS() {
        return new Permission("approve_transfers");
    }

    public String asString() {
        return value;
    }

    @Override
    public String toString() {
        return "Permission{" + value + '}';
    }
}
