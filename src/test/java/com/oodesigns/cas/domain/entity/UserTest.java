package com.oodesigns.cas.domain.entity;

import com.oodesigns.cas.domain.value.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for User domain entity.
 * Validates: factory methods, immutability, state changes return new instances, roles management.
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
        
        assertEquals(userId, user.getUserId());
        assertEquals(username, user.getUsername());
        assertEquals(passwordHash, user.getPasswordHash());
        assertTrue(user.getRoles().isEmpty());
        assertTrue(user.isForcePasswordReset());  // New users must change password
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
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
    public void testRestoreUser() {
        Set<Role> roles = new HashSet<>();
        roles.add(Role.user());
        Instant createdAt = Instant.now().minusSeconds(3600);
        Instant updatedAt = Instant.now();

        User user = User.restore(userId, username, passwordHash, roles, new java.util.HashSet<>(), true, createdAt, updatedAt);

        assertEquals(userId, user.getUserId());
        assertEquals(username, user.getUsername());
        assertEquals(passwordHash, user.getPasswordHash());
        assertEquals(roles, user.getRoles());
        assertTrue(user.isForcePasswordReset());
        assertEquals(createdAt, user.getCreatedAt());
        assertEquals(updatedAt, user.getUpdatedAt());
    }

    @Test
    public void testAssignRoleReturnsNewInstance() {
        User user1 = User.create(userId, username, passwordHash);
        User user2 = user1.assignRole(Role.user());

        // Both users are the same entity (same userId) but different instances
        assertEquals(user1, user2);  // Same user ID = equal
        assertNotSame(user1, user2);  // But different objects
        
        // Original state unchanged
        assertTrue(user1.getRoles().isEmpty());
        
        // New instance has role
        assertEquals(1, user2.getRoles().size());
        assertTrue(user2.hasRole(Role.user()));
    }

    @Test
    public void testAssignRoleIdempotent() {
        User user1 = User.create(userId, username, passwordHash).assignRole(Role.user());
        User user2 = user1.assignRole(Role.user());

        // Same size (role not duplicated)
        assertEquals(1, user2.getRoles().size());
    }

    @Test
    public void testAssignMultipleRoles() {
        User user = User.create(userId, username, passwordHash)
            .assignRole(Role.user())
            .assignRole(Role.admin());

        assertEquals(2, user.getRoles().size());
        assertTrue(user.hasRole(Role.user()));
        assertTrue(user.hasRole(Role.admin()));
    }

    @Test
    public void testHasRoleFalse() {
        User user = User.create(userId, username, passwordHash);
        assertFalse(user.hasRole(Role.admin()));
    }

    @Test
    public void testIsAdminTrue() {
        User user = User.create(userId, username, passwordHash)
            .assignRole(Role.admin());
        assertTrue(user.isAdmin());
    }

    @Test
    public void testIsAdminFalse() {
        User user = User.create(userId, username, passwordHash)
            .assignRole(Role.user());
        assertFalse(user.isAdmin());
    }

    @Test
    public void testClearForcePasswordResetReturnsNewInstance() {
        Instant createdAt = Instant.now();
        Instant updatedAt = Instant.now();
        User user1 = User.restore(userId, username, passwordHash, new HashSet<>(), new java.util.HashSet<>(), true, createdAt, updatedAt);
        User user2 = user1.clearForcePasswordReset();

        // Both users are the same entity (same userId) but different instances
        assertEquals(user1, user2);  // Same user ID = equal
        assertNotSame(user1, user2);  // But different objects
        
        // Original state unchanged
        assertTrue(user1.isForcePasswordReset());
        
        // New instance cleared
        assertFalse(user2.isForcePasswordReset());
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
    public void testImmutabilityGetRolesReturnsNewSet() {
        User user = User.create(userId, username, passwordHash)
            .assignRole(Role.user());
        
        Set<Role> roles = user.getRoles();
        
        // getRoles returns unmodifiable set
        assertThrows(UnsupportedOperationException.class, () -> roles.add(Role.admin()));

        // Original user unchanged
        assertEquals(1, user.getRoles().size());
    }

    @Test
    public void testCreatedAtAndUpdatedAtNeverNull() {
        User user = User.create(userId, username, passwordHash);
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
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
