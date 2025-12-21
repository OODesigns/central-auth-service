package com.oodesigns.cas.domain.value;

import java.util.Objects;

/**
 * Value object representing a fine-grained permission.
 * Examples: view_users, edit_profile, delete_accounts, approve_transfers
 */
public final class Permission {
    private final String value;

    private Permission(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Permission cannot be null or blank");
        }
        // Validate format: lowercase alphanumeric with underscores
        if (!value.matches("^[a-z0-9_]+$")) {
            throw new IllegalArgumentException("Permission must be lowercase alphanumeric with underscores");
        }
        this.value = value;
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
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Permission)) return false;
        Permission that = (Permission) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "Permission{" + value + '}';
    }
}
