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
        assertEquals("view_users", perm.asString());
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

        assertEquals("view_users", view.asString());
        assertEquals("edit_profile", edit.asString());
        assertEquals("view_reports", reports.asString());
        assertEquals("manage_users", manage.asString());
        assertEquals("delete_accounts", delete.asString());
        assertEquals("approve_transfers", approve.asString());
    }

    @Test
    void testPermissionToString() {
        Permission perm = Permission.of("delete_accounts");
        assertTrue(perm.toString().contains("delete_accounts"));
    }

    @Test
    void testValidPermissionFormats() {
        Permission perm1 = Permission.of("view_users");
        Permission perm2 = Permission.of("admin_123");
        Permission perm3 = Permission.of("a");
        Permission perm4 = Permission.of("view_users_and_roles");

        assertEquals("view_users", perm1.asString());
        assertEquals("admin_123", perm2.asString());
        assertEquals("a", perm3.asString());
        assertEquals("view_users_and_roles", perm4.asString());
    }
}
