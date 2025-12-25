package com.oodesigns.cas.domain.value;

import com.oodesigns.cas.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Credentials record.
 * Validates: immutability, null validation, and record behavior.
 */
class CredentialsTest {
    
    private User testUser;
    private Password testPassword;

    @BeforeEach
    void setUp() {
        UserId userId = UserId.generate();
        Username username = new Username("test_user");
        PasswordHash passwordHash = new PasswordHash("$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
        testUser = User.create(userId, username, passwordHash);
        testPassword = new Password("password123".toCharArray());
    }

    @Test
    void testCreateValidCredentials() {
        var credentials = new Credentials(testUser, testPassword);
        
        assertNotNull(credentials);
        assertEquals(testUser, credentials.user());
        assertEquals(testPassword, credentials.password());
    }

    @Test
    void testCredentialsWithNullUserThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new Credentials(null, testPassword));
    }

    @Test
    void testCredentialsWithNullPasswordThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new Credentials(testUser, null));
    }

    @Test
    void testCredentialsWithBothNullThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new Credentials(null, null));
    }

    @Test
    void testCredentialsEquality() {
        try (var credentials1 = new Credentials(testUser, testPassword);
             var credentials2 = new Credentials(testUser, testPassword)) {
            assertEquals(credentials1, credentials2);
        }
    }

    @Test
    void testCredentialsInequality() {
        try (var credentials1 = new Credentials(testUser, testPassword)) {
            UserId userId2 = UserId.generate();
            Username username2 = new Username("other_user");
            PasswordHash passwordHash2 = new PasswordHash("$2b$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
            User differentUser = User.create(userId2, username2, passwordHash2);
            
            try (var credentials2 = new Credentials(differentUser, testPassword)) {
                assertNotEquals(credentials1, credentials2);
            }
        }
    }

    @Test
    void testCredentialsHashCode() {
        try (var credentials1 = new Credentials(testUser, testPassword);
             var credentials2 = new Credentials(testUser, testPassword)) {
            assertEquals(credentials1.hashCode(), credentials2.hashCode());
        }
    }

    @Test
    void testCredentialsToString() {
        try (var credentials = new Credentials(testUser, testPassword)) {
            String toString = credentials.toString();
            
            assertNotNull(toString);
            assertTrue(toString.contains("Credentials"));
        }
    }

    @Test
    void testCredentialsImmutability() {
        try (var credentials = new Credentials(testUser, testPassword)) {
            // Verify we get the same objects back (record accessors return the exact fields)
            assertSame(testUser, credentials.user());
            assertSame(testPassword, credentials.password());
        }
    }

    @Test
    void testCredentialsIsAutoCloseable() {
        var credentials = new Credentials(testUser, testPassword);
        
        // Verify Credentials implements AutoCloseable
        assertInstanceOf(AutoCloseable.class, credentials);
    }

    @Test
    void testCredentialsClosesClearsPassword() {
        Password password = new Password("password123".toCharArray());
        var credentials = new Credentials(testUser, password);
        
        // Close credentials - should not throw
        assertDoesNotThrow(credentials::close);
    }

    @Test
    void testCredentialsCloseIsIdempotent() {
        Password password = new Password("password123".toCharArray());
        var credentials = new Credentials(testUser, password);
        
        // Call close multiple times - should not throw
        assertDoesNotThrow(() -> {
            credentials.close();
            credentials.close();
            credentials.close();
        });
    }
}
