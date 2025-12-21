package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Permission value object.
 * Validates: creation, format validation, equality, standard permission constants.
 */
public class PermissionTest {

    @Test
    public void testCreatePermission() {
        Permission perm = Permission.of("view_users");
        assertEquals("view_users", perm.asString());
    }

    @Test
    public void testPermissionEquality() {
        Permission perm1 = Permission.of("view_users");
        Permission perm2 = Permission.of("view_users");
        assertEquals(perm1, perm2);
    }

    @Test
    public void testPermissionInequality() {
        Permission perm1 = Permission.of("view_users");
        Permission perm2 = Permission.of("manage_users");
        assertNotEquals(perm1, perm2);
    }

    @Test
    public void testPermissionHashCode() {
        Permission perm1 = Permission.of("view_users");
        Permission perm2 = Permission.of("view_users");
        assertEquals(perm1.hashCode(), perm2.hashCode());
    }

    @Test
    public void testNullPermissionThrows() {
        assertThrows(IllegalArgumentException.class, () -> Permission.of(null));
    }

    @Test
    public void testBlankPermissionThrows() {
        assertThrows(IllegalArgumentException.class, () -> Permission.of(""));
        assertThrows(IllegalArgumentException.class, () -> Permission.of("   "));
    }

    @Test
    public void testInvalidPermissionFormatThrows() {
        // Uppercase not allowed
        assertThrows(IllegalArgumentException.class, () -> Permission.of("View_Users"));
        // Hyphens not allowed
        assertThrows(IllegalArgumentException.class, () -> Permission.of("view-users"));
        // Dots not allowed
        assertThrows(IllegalArgumentException.class, () -> Permission.of("view.users"));
    }

    @Test
    public void testStandardPermissionConstants() {
        Permission view = Permission.VIEW_USERS();
        Permission edit = Permission.EDIT_PROFILE();
        Permission reports = Permission.VIEW_REPORTS();
        Permission manage = Permission.MANAGE_USERS();
        Permission delete = Permission.DELETE_ACCOUNTS();
        Permission approve = Permission.APPROVE_TRANSFERS();

        assertEquals("view_users", view.asString());
        assertEquals("edit_profile", edit.asString());
        assertEquals("view_reports", reports.asString());
        assertEquals("manage_users", manage.asString());
        assertEquals("delete_accounts", delete.asString());
        assertEquals("approve_transfers", approve.asString());
    }

    @Test
    public void testPermissionToString() {
        Permission perm = Permission.of("delete_accounts");
        assertTrue(perm.toString().contains("delete_accounts"));
    }

    @Test
    public void testValidPermissionFormats() {
        Permission perm1 = Permission.of("view_users");
        Permission perm2 = Permission.of("admin_123");
        Permission perm3 = Permission.of("a");
        Permission perm4 = Permission.of("VIEW_USERS_AND_ROLES".toLowerCase());

        assertEquals("view_users", perm1.asString());
        assertEquals("admin_123", perm2.asString());
        assertEquals("a", perm3.asString());
    }
}
