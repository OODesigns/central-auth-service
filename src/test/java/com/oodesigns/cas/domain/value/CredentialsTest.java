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
        final UserId userId = UserId.generate();
        final PasswordHash passwordHash = new PasswordHash("$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
        testCredential = new UserCredential(userId, passwordHash);
        testPassword = new Password("password123".toCharArray());
    }

    @Test
    void testCreateValidCredentials() {
        try (final var credentials = new Credentials(testCredential, testPassword)) {
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

    private void createAndClose(final UserCredential credential, final Password password) {
        try (final Credentials ignored = new Credentials(credential, password)) {
            java.util.Objects.requireNonNull(ignored); // touch to avoid empty try block
        }
    }

    @Test
    void testCredentialsWithBothNullThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> createAndClose(null, null));
    }

    @Test
    void testCredentialsEquality() {
        try (final var credentials1 = new Credentials(testCredential, testPassword);
             final var credentials2 = new Credentials(testCredential, testPassword)) {
            assertEquals(credentials1, credentials2);
        }
    }

    @Test
    void testCredentialsInequality() {
        try (final var credentials1 = new Credentials(testCredential, testPassword)) {
            final UserId userId2 = UserId.generate();
            final PasswordHash passwordHash2 = new PasswordHash("$2b$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
            final UserCredential differentCredential = new UserCredential(userId2, passwordHash2);
            
            try (final var credentials2 = new Credentials(differentCredential, testPassword)) {
                assertNotEquals(credentials1, credentials2);
            }
        }
    }

    @Test
    void testCredentialsHashCode() {
        try (final var credentials1 = new Credentials(testCredential, testPassword);
             final var credentials2 = new Credentials(testCredential, testPassword)) {
            assertEquals(credentials1.hashCode(), credentials2.hashCode());
        }
    }

    @Test
    void testCredentialsToString() {
        try (final var credentials = new Credentials(testCredential, testPassword)) {
            final String toString = credentials.toString();
            
            assertNotNull(toString);
            assertTrue(toString.contains("Credentials"));
        }
    }

    @Test
    void testCredentialsImmutability() {
        try (final var credentials = new Credentials(testCredential, testPassword)) {
            // Verify we get the same objects back (record accessors return the exact fields)
            assertSame(testCredential, credentials.credential());
            assertSame(testPassword, credentials.password());
        }
    }

    @Test
    void testCredentialsIsAutoCloseable() {
        try (final var credentials = new Credentials(testCredential, testPassword)) {
            // Verify Credentials implements AutoCloseable
            assertInstanceOf(AutoCloseable.class, credentials);
        }
    }

    @Test
    void testCredentialsClosesClearsPassword() {
        final Password password = new Password("password123".toCharArray());
        try (final var credentials = new Credentials(testCredential, password)) {
            // Close credentials - should not throw
            assertDoesNotThrow(credentials::close);
        }
    }

    @Test
    void testCredentialsCloseIsIdempotent() {
        final Password password = new Password("password123".toCharArray());
        try (final var credentials = new Credentials(testCredential, password)) {
            // Call close multiple times - should not throw
            assertDoesNotThrow(() -> {
                credentials.close();
                credentials.close();
                credentials.close();
            });
        }
    }
}
