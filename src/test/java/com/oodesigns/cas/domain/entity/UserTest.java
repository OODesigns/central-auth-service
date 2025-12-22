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
public class UserTest {

    private UserId userId;
    private Username username;
    private PasswordHash passwordHash;

    @BeforeEach
    public void setUp() {
        userId = UserId.generate();
        username = new Username("john_doe");
        passwordHash = new PasswordHash("$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
    }

    @Test
    public void testCreateNewUser() {
        User user = User.create(userId, username, passwordHash);
        
        assertEquals(userId, user.userId());
        assertEquals(username, user.username());
        assertEquals(passwordHash, user.passwordHash());
        assertTrue(user.permissions().isEmpty());
    }

    @Test
    public void testCreateThrowsWithNullUserId() {
        assertThrows(NullPointerException.class, 
            () -> User.create(null, username, passwordHash));
    }

    @Test
    public void testCreateThrowsWithNullUsername() {
        assertThrows(NullPointerException.class, 
            () -> User.create(userId, null, passwordHash));
    }

    @Test
    public void testCreateThrowsWithNullPasswordHash() {
        assertThrows(NullPointerException.class, 
            () -> User.create(userId, username, null));
    }

    @Test
    public void testGrantPermissionReturnsNewInstance() {
        User user1 = User.create(userId, username, passwordHash);
        User user2 = user1.grantPermission(Permission.VIEW_USERS());

        // Both users are the same entity (same userId) but different instances
        assertEquals(user1, user2);  // Same user ID = equal
        assertNotSame(user1, user2);  // But different objects
        
        // Original state unchanged
        assertTrue(user1.permissions().isEmpty());
        
        // New instance has permission
        assertEquals(1, user2.permissions().size());
        assertTrue(user2.permissions().contains(Permission.VIEW_USERS()));
    }

    @Test
    public void testGrantMultiplePermissions() {
        User user = User.create(userId, username, passwordHash)
            .grantPermission(Permission.VIEW_USERS())
            .grantPermission(Permission.EDIT_PROFILE());

        assertEquals(2, user.permissions().size());
        assertTrue(user.permissions().contains(Permission.VIEW_USERS()));
        assertTrue(user.permissions().contains(Permission.EDIT_PROFILE()));
    }

    @Test
    public void testEqualityBasedOnUserId() {
        User user1 = User.create(userId, username, passwordHash);
        User user2 = User.create(userId, new Username("different"), passwordHash);
        
        assertEquals(user1, user2);
    }

    @Test
    public void testInequalityDifferentUserIds() {
        User user1 = User.create(userId, username, passwordHash);
        User user2 = User.create(UserId.generate(), username, passwordHash);
        
        assertNotEquals(user1, user2);
    }

    @Test
    public void testHashCodeConsistency() {
        User user1 = User.create(userId, username, passwordHash);
        User user2 = User.create(userId, new Username("different"), passwordHash);
        
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    public void testImmutabilityGetPermissionsReturnsUnmodifiable() {
        User user = User.create(userId, username, passwordHash)
            .grantPermission(Permission.VIEW_USERS());
        
        Set<Permission> permissions = user.permissions();
        
        // permissions returns unmodifiable set
        assertThrows(UnsupportedOperationException.class, () -> permissions.add(Permission.EDIT_PROFILE()));

        // Original user unchanged
        assertEquals(1, user.permissions().size());
    }

    @Test
    public void testCanBeUsedInHashBasedCollections() {
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
