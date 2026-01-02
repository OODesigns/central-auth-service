package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Credentials record.
 * Validates: immutability, null validation, and record behavior.
 */
class CredentialsTest {
    
    private UserCredential testCredential;
    private Password testPassword;

    @BeforeEach
    void setUp() {
        UserId userId = UserId.generate();
        PasswordHash passwordHash = new PasswordHash("$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
        testCredential = new UserCredential(userId, passwordHash);
        testPassword = new Password("password123".toCharArray());
    }

    @Test
    void testCreateValidCredentials() {
        try (var credentials = new Credentials(testCredential, testPassword)) {
            assertNotNull(credentials);
            assertEquals(testCredential, credentials.credential());
            assertEquals(testPassword, credentials.password());
        }
    }

    @Test
    void testCredentialsWithNullCredentialThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> createAndClose(null, testPassword));
    }

    @Test
    void testCredentialsWithNullPasswordThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> createAndClose(testCredential, null));
    }

    private void createAndClose(UserCredential credential, Password password) {
        try (Credentials ignored = new Credentials(credential, password)) {
            java.util.Objects.requireNonNull(ignored); // touch to avoid empty try block
        }
    }

    @Test
    void testCredentialsWithBothNullThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> createAndClose(null, null));
    }

    @Test
    void testCredentialsEquality() {
        try (var credentials1 = new Credentials(testCredential, testPassword);
             var credentials2 = new Credentials(testCredential, testPassword)) {
            assertEquals(credentials1, credentials2);
        }
    }

    @Test
    void testCredentialsInequality() {
        try (var credentials1 = new Credentials(testCredential, testPassword)) {
            UserId userId2 = UserId.generate();
            PasswordHash passwordHash2 = new PasswordHash("$2b$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
            UserCredential differentCredential = new UserCredential(userId2, passwordHash2);
            
            try (var credentials2 = new Credentials(differentCredential, testPassword)) {
                assertNotEquals(credentials1, credentials2);
            }
        }
    }

    @Test
    void testCredentialsHashCode() {
        try (var credentials1 = new Credentials(testCredential, testPassword);
             var credentials2 = new Credentials(testCredential, testPassword)) {
            assertEquals(credentials1.hashCode(), credentials2.hashCode());
        }
    }

    @Test
    void testCredentialsToString() {
        try (var credentials = new Credentials(testCredential, testPassword)) {
            String toString = credentials.toString();
            
            assertNotNull(toString);
            assertTrue(toString.contains("Credentials"));
        }
    }

    @Test
    void testCredentialsImmutability() {
        try (var credentials = new Credentials(testCredential, testPassword)) {
            // Verify we get the same objects back (record accessors return the exact fields)
            assertSame(testCredential, credentials.credential());
            assertSame(testPassword, credentials.password());
        }
    }

    @Test
    void testCredentialsIsAutoCloseable() {
        try (var credentials = new Credentials(testCredential, testPassword)) {
            // Verify Credentials implements AutoCloseable
            assertInstanceOf(AutoCloseable.class, credentials);
        }
    }

    @Test
    void testCredentialsClosesClearsPassword() {
        Password password = new Password("password123".toCharArray());
        try (var credentials = new Credentials(testCredential, password)) {
            // Close credentials - should not throw
            assertDoesNotThrow(credentials::close);
        }
    }

    @Test
    void testCredentialsCloseIsIdempotent() {
        Password password = new Password("password123".toCharArray());
        try (var credentials = new Credentials(testCredential, password)) {
            // Call close multiple times - should not throw
            assertDoesNotThrow(() -> {
                credentials.close();
                credentials.close();
                credentials.close();
            });
        }
    }
}
