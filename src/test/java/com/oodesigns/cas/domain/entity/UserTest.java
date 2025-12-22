package com.oodesigns.cas.domain.entity;

import com.oodesigns.cas.domain.value.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for User domain entity.
 * Validates: factory methods, immutability, permissions management.
 */
class UserTest {

    private UserId userId;
    private Username username;
    private PasswordHash passwordHash;

    @BeforeEach
    void setUp() {
        userId = UserId.generate();
        username = new Username("john_doe");
        passwordHash = new PasswordHash("$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
    }

    @Test
    void testCreateNewUser() {
        User user = User.create(userId, username, passwordHash);
        
        assertEquals(userId, user.userId());
        assertEquals(username, user.username());
        assertEquals(passwordHash, user.passwordHash());
        assertTrue(user.permissions().isEmpty());
    }

    @Test
    void testCreateThrowsWithNullUserId() {
        assertThrows(NullPointerException.class, () -> User.create(null, username, passwordHash));
    }

    @Test
    void testCreateThrowsWithNullUsername() {
        assertThrows(NullPointerException.class, () -> User.create(userId, null, passwordHash));
    }

    @Test
    void testCreateThrowsWithNullPasswordHash() {
        assertThrows(NullPointerException.class, () -> User.create(userId, username, null));
    }

    @Test
    void testGrantPermissionReturnsNewInstance() {
        User user1 = User.create(userId, username, passwordHash);
        User user2 = user1.grantPermission(Permission.of("view_users"));

        // Both users are the same entity (same userId) but different instances
        assertEquals(user1, user2);  // Same user ID = equal
        assertNotSame(user1, user2);  // But different objects
        
        // Original state unchanged
        assertTrue(user1.permissions().isEmpty());
        
        // New instance has permission
        assertEquals(1, user2.permissions().size());
        assertTrue(user2.permissions().contains(Permission.of("view_users")));
    }

    @Test
    void testGrantMultiplePermissions() {
        User user = User.create(userId, username, passwordHash)
            .grantPermission(Permission.of("view_users"))
            .grantPermission(Permission.of("edit_profile"));

        assertEquals(2, user.permissions().size());
        assertTrue(user.permissions().contains(Permission.of("view_users")));
        assertTrue(user.permissions().contains(Permission.of("edit_profile")));
    }

    @Test
    void testEqualityBasedOnUserId() {
        User user1 = User.create(userId, username, passwordHash);
        User user2 = User.create(userId, new Username("different"), passwordHash);
        
        assertEquals(user1, user2);
    }

    @Test
    void testInequalityDifferentUserIds() {
        User user1 = User.create(userId, username, passwordHash);
        User user2 = User.create(UserId.generate(), username, passwordHash);
        
        assertNotEquals(user1, user2);
    }

    @Test
    void testHashCodeConsistency() {
        User user1 = User.create(userId, username, passwordHash);
        User user2 = User.create(userId, new Username("different"), passwordHash);
        
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void testImmutabilityGetPermissionsReturnsUnmodifiable() {
        User user = User.create(userId, username, passwordHash)
            .grantPermission(Permission.of("view_users"));
        
        Set<Permission> permissions = user.permissions();
        
        // permissions returns unmodifiable set
        Permission editProfile = Permission.of("edit_profile");
        assertThrows(UnsupportedOperationException.class, () -> permissions.add(editProfile));

        // Original user unchanged
        assertEquals(1, user.permissions().size());
    }

    @Test
    void testCanBeUsedInHashBasedCollections() {
        User user1 = User.create(userId, username, passwordHash);
        User user2 = User.create(UserId.generate(), username, passwordHash);

        Set<User> users = new HashSet<>();
        users.add(user1);
        users.add(user2);

        assertEquals(2, users.size());
        assertTrue(users.contains(user1));
        assertTrue(users.contains(user2));
    }
}