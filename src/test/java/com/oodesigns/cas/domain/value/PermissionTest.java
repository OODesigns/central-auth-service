package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Permission value object.
 * Validates: creation, format validation, equality, standard permission constants.
 */
class PermissionTest {

    @Test
    void testCreatePermission() {
        Permission perm = Permission.of("view_users");
        assertEquals("view_users", perm.value());
    }

    @Test
    void testPermissionEquality() {
        Permission perm1 = Permission.of("view_users");
        Permission perm2 = Permission.of("view_users");
        assertEquals(perm1, perm2);
    }

    @Test
    void testPermissionInequality() {
        Permission perm1 = Permission.of("view_users");
        Permission perm2 = Permission.of("manage_users");
        assertNotEquals(perm1, perm2);
    }

    @Test
    void testPermissionHashCode() {
        Permission perm1 = Permission.of("view_users");
        Permission perm2 = Permission.of("view_users");
        assertEquals(perm1.hashCode(), perm2.hashCode());
    }

    @Test
    void testNullPermissionThrows() {
        assertThrows(IllegalArgumentException.class, () -> Permission.of(null));
    }

    @Test
    void testBlankPermissionThrows() {
        assertThrows(IllegalArgumentException.class, () -> Permission.of(""));
        assertThrows(IllegalArgumentException.class, () -> Permission.of("   "));
    }

    @Test
    void testInvalidPermissionFormatThrows() {
        // Uppercase not allowed
        assertThrows(IllegalArgumentException.class, () -> Permission.of("View_Users"));
        // Hyphens not allowed
        assertThrows(IllegalArgumentException.class, () -> Permission.of("view-users"));
        // Dots not allowed
        assertThrows(IllegalArgumentException.class, () -> Permission.of("view.users"));
    }

    @Test
    void testStandardPermissionConstants() {
        Permission view = Permission.of("view_users");
        Permission edit = Permission.of("edit_profile");
        Permission reports = Permission.of("view_reports");
        Permission manage = Permission.of("manage_users");
        Permission delete = Permission.of("delete_accounts");
        Permission approve = Permission.of("approve_transfers");

        assertEquals("view_users", view.value());
        assertEquals("edit_profile", edit.value());
        assertEquals("view_reports", reports.value());
        assertEquals("manage_users", manage.value());
        assertEquals("delete_accounts", delete.value());
        assertEquals("approve_transfers", approve.value());
    }

    @Test
    void testPermissionToString() {
        Permission perm = Permission.of("delete_accounts");
        assertEquals("delete_accounts", perm.toString());
    }

    @Test
    void testFactoryMethods() {
        Permission perm1 = Permission.of("view_users");
        Permission perm2 = Permission.of("admin_123");
        Permission perm3 = Permission.of("a");
        Permission perm4 = Permission.of("view_users_and_roles");

        assertEquals("view_users", perm1.value());
        assertEquals("admin_123", perm2.value());
        assertEquals("a", perm3.value());
        assertEquals("view_users_and_roles", perm4.value());
    }
}
