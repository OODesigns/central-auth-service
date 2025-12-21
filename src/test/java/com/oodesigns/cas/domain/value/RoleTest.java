package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Role value object.
 * Validates: enum-based roles, factory methods, equality.
 */
public class RoleTest {

    @Test
    public void testAdminRole() {
        Role role = Role.admin();
        assertEquals("admin", role.asString());
    }

    @Test
    public void testUserRole() {
        Role role = Role.user();
        assertEquals("user", role.asString());
    }

    @Test
    public void testKioskRole() {
        Role role = Role.kiosk();
        assertEquals("kiosk", role.asString());
    }

    @Test
    public void testFromString() {
        Role role = new Role(Role.RoleName.of("admin"));
        assertEquals(Role.admin(), role);
    }

    @Test
    public void testFromStringNormalizesCase() {
        Role role = new Role(Role.RoleName.of("ADMIN"));
        assertEquals(Role.admin(), role);
    }

    @Test
    public void testFromInvalidStringThrows() {
        assertThrows(IllegalArgumentException.class, () -> Role.RoleName.of("INVALID"));
    }

    @Test
    public void testNullFromStringThrows() {
        assertThrows(IllegalArgumentException.class, () -> Role.RoleName.of(null));
    }

    @Test
    public void testEqualityAdminRole() {
        Role role1 = Role.admin();
        Role role2 = Role.admin();
        assertEquals(role1, role2);
    }

    @Test
    public void testInequalityDifferentRoles() {
        Role role1 = Role.admin();
        Role role2 = Role.user();
        assertNotEquals(role1, role2);
    }

    @Test
    public void testHashCodeConsistency() {
        Role role1 = Role.admin();
        Role role2 = Role.admin();
        assertEquals(role1.hashCode(), role2.hashCode());
    }

    @Test
    public void testAllRolesDistinct() {
        Role admin = Role.admin();
        Role user = Role.user();
        Role kiosk = Role.kiosk();

        assertNotEquals(admin, user);
        assertNotEquals(admin, kiosk);
        assertNotEquals(user, kiosk);
    }
}
