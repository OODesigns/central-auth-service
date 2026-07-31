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
        userId = UserId.of(UUID.randomUUID());
        username = Username.of("john_doe");
    }

    @Test
    void testCreateNewUser() {
        final User user = new User(userId, username, Set.of(), null, null);
        
        assertEquals(userId, user.userId());
        assertEquals(username, user.username());
        assertTrue(user.permissions().isEmpty());
    }

    @Test
    void testCreateThrowsWithNullUserId() {
        final Set<Permission> permissions = Set.of();
        assertThrows(NullPointerException.class, () -> new User(null, username, permissions, null, null));
    }

    @Test
    void testCreateThrowsWithNullUsername() {
        final Set<Permission> permissions = Set.of();
        assertThrows(NullPointerException.class, () -> new User(userId, null, permissions, null, null));
    }

    @Test
    void testGrantMultiplePermissions() {
        final User user = new User(userId, username, Set.of(
            Permission.of("view_users"),
            Permission.of("edit_profile")
        ), null, null);

        assertEquals(2, user.permissions().size());
        assertTrue(user.permissions().contains(Permission.of("view_users")));
        assertTrue(user.permissions().contains(Permission.of("edit_profile")));
    }

    @Test
    void testEqualityBasedOnAllFields() {
        final User user1 = new User(userId, username, Set.of(), null, null);
        final User user2 = new User(userId, username, Set.of(), null, null);
        
        assertEquals(user1, user2);
    }

    @Test
    void testInequalityDifferentUserIds() {
        final User user1 = new User(userId, username, Set.of(), null, null);
        final User user2 = new User(UserId.of(UUID.randomUUID()), username, Set.of(), null, null);
        
        assertNotEquals(user1, user2);
    }

    @Test
    void testInequalityDifferentUsernames() {
        final User user1 = new User(userId, username, Set.of(), null, null);
        final User user2 = new User(userId, Username.of("different"), Set.of(), null, null);
        
        assertNotEquals(user1, user2);
    }

    @Test
    void testHashCodeConsistency() {
        final User user1 = new User(userId, username, Set.of(), null, null);
        final User user2 = new User(userId, username, Set.of(), null, null);
        
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void testImmutabilityGetPermissionsReturnsUnmodifiable() {
        final User user = new User(userId, username, Set.of(Permission.of("view_users")), null, null);

        final Set<Permission> permissions = user.permissions();
        
        // permissions returns unmodifiable set
        final Permission editProfile = Permission.of("edit_profile");
        assertThrows(UnsupportedOperationException.class, () -> permissions.add(editProfile));

        // Original user unchanged
        assertEquals(1, user.permissions().size());
    }

    @Test
    void testCanBeUsedInHashBasedCollections() {
        final User user1 = new User(userId, username, Set.of(), null, null);
        final User user2 = new User(UserId.of(UUID.randomUUID()), username, Set.of(), null, null);

        final Set<User> users = new HashSet<>();
        users.add(user1);
        users.add(user2);

        assertEquals(2, users.size());
        assertTrue(users.contains(user1));
        assertTrue(users.contains(user2));
    }

    @Test
    void testToStringContainsUserIdAndUsername() {
        final User user = new User(userId, username, Set.of(), null, null);
        
        final String str = user.toString();
        
        assertTrue(str.contains("User{"), "Should contain class name");
        assertTrue(str.contains(userId.toString()), "Should contain userId");
        assertTrue(str.contains(username.value()), "Should contain username");
    }

    @Test
    void testCreateThrowsWithNullPermissions() {
        assertThrows(NullPointerException.class, () -> new User(userId, username, null, null, null));
    }
}