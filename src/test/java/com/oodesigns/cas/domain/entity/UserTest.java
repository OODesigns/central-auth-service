package com.oodesigns.cas.domain.entity;

import com.oodesigns.cas.domain.value.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for User domain entity.
 * Validates: factory methods, immutability, permissions management.
 * Note: User contains only post-authentication data (no password hash).
 */
class UserTest {

    private UserId userId;
    private Username username;

    @BeforeEach
    void setUp() {
        userId = UserId.generate();
        username = new Username("john_doe");
    }

    @Test
    void testCreateNewUser() {
        User user = new User(userId, username, Set.of());
        
        assertEquals(userId, user.userId());
        assertEquals(username, user.username());
        assertTrue(user.permissions().isEmpty());
    }

    @Test
    void testCreateThrowsWithNullUserId() {
        UserId nullUserId = null;
        Set<Permission> permissions = Set.of();
        assertThrows(NullPointerException.class, () -> new User(nullUserId, username, permissions));
    }

    @Test
    void testCreateThrowsWithNullUsername() {
        Username nullUsername = null;
        Set<Permission> permissions = Set.of();
        assertThrows(NullPointerException.class, () -> new User(userId, nullUsername, permissions));
    }

    @Test
    void testGrantMultiplePermissions() {
        User user = new User(userId, username, Set.of(
            Permission.of("view_users"),
            Permission.of("edit_profile")
        ));

        assertEquals(2, user.permissions().size());
        assertTrue(user.permissions().contains(Permission.of("view_users")));
        assertTrue(user.permissions().contains(Permission.of("edit_profile")));
    }

    @Test
    void testEqualityBasedOnAllFields() {
        User user1 = new User(userId, username, Set.of());
        User user2 = new User(userId, username, Set.of());
        
        assertEquals(user1, user2);
    }

    @Test
    void testInequalityDifferentUserIds() {
        User user1 = new User(userId, username, Set.of());
        User user2 = new User(UserId.generate(), username, Set.of());
        
        assertNotEquals(user1, user2);
    }

    @Test
    void testInequalityDifferentUsernames() {
        User user1 = new User(userId, username, Set.of());
        User user2 = new User(userId, new Username("different"), Set.of());
        
        assertNotEquals(user1, user2);
    }

    @Test
    void testHashCodeConsistency() {
        User user1 = new User(userId, username, Set.of());
        User user2 = new User(userId, username, Set.of());
        
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void testImmutabilityGetPermissionsReturnsUnmodifiable() {
        User user = new User(userId, username, Set.of(Permission.of("view_users")));
        
        Set<Permission> permissions = user.permissions();
        
        // permissions returns unmodifiable set
        Permission editProfile = Permission.of("edit_profile");
        assertThrows(UnsupportedOperationException.class, () -> permissions.add(editProfile));

        // Original user unchanged
        assertEquals(1, user.permissions().size());
    }

    @Test
    void testCanBeUsedInHashBasedCollections() {
        User user1 = new User(userId, username, Set.of());
        User user2 = new User(UserId.generate(), username, Set.of());

        Set<User> users = new HashSet<>();
        users.add(user1);
        users.add(user2);

        assertEquals(2, users.size());
        assertTrue(users.contains(user1));
        assertTrue(users.contains(user2));
    }
}