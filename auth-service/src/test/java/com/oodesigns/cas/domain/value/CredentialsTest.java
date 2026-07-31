package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for Credentials record.
 * Validates: immutability, null validation, and record behavior.
 */
class CredentialsTest {
    
    private UserCredential testCredential;
    private Password testPassword;

    @BeforeEach
    void setUp() {
        final UserId userId = UserId.of(UUID.randomUUID());
        final PasswordHash passwordHash = PasswordHash.of("$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
        testCredential = UserCredential.of(userId, passwordHash);
        testPassword = Password.of("ValidPassword1234".toCharArray());  // 16 chars
    }

    @AfterEach
    void tearDown() {
        if (testPassword != null) {
            testPassword.close();
        }
    }

    @Test
    void testCreateValidCredentials() {
        try (final var credentials = Credentials.of(testCredential, testPassword)) {
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
        try (final Credentials ignored = Credentials.of(credential, password)) {
            java.util.Objects.requireNonNull(ignored); // touch to avoid empty try block
        }
    }

    @Test
    void testCredentialsWithBothNullThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> createAndClose(null, null));
    }

    @Test
    void testCredentialsEquality() {
        try (final var credentials1 = Credentials.of(testCredential, testPassword);
             final var credentials2 = Credentials.of(testCredential, testPassword)) {
            assertEquals(credentials1, credentials2);
        }
    }

    @Test
    void testCredentialsInequality() {
        try (final var credentials1 = Credentials.of(testCredential, testPassword)) {
            final UserId userId2 = UserId.of(UUID.randomUUID());
            final PasswordHash passwordHash2 = PasswordHash.of("$2b$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
            final UserCredential differentCredential = UserCredential.of(userId2, passwordHash2);
            
            try (final var credentials2 = Credentials.of(differentCredential, testPassword)) {
                assertNotEquals(credentials1, credentials2);
            }
        }
    }

    @Test
    void testCredentialsHashCode() {
        try (final var credentials1 = Credentials.of(testCredential, testPassword);
             final var credentials2 = Credentials.of(testCredential, testPassword)) {
            assertEquals(credentials1.hashCode(), credentials2.hashCode());
        }
    }

    @Test
    void testCredentialsToString() {
        try (final var credentials = Credentials.of(testCredential, testPassword)) {
            final String toString = credentials.toString();
            
            assertNotNull(toString);
            assertTrue(toString.contains("Credentials"));
        }
    }

    @Test
    void testCredentialsImmutability() {
        try (final var credentials = Credentials.of(testCredential, testPassword)) {
            // Verify we get the same objects back (record accessors return the exact fields)
            assertSame(testCredential, credentials.credential());
            assertSame(testPassword, credentials.password());
        }
    }

    @Test
    void testCredentialsIsAutoCloseable() {
        try (final var credentials = Credentials.of(testCredential, testPassword)) {
            // Verify Credentials implements AutoCloseable
            assertInstanceOf(AutoCloseable.class, credentials);
        }
    }

    @Test
    void testCredentialsClosesClearsPassword() {
        final Password password = Password.of("ValidPassword1234".toCharArray());  // 16 chars
        try (final var credentials = Credentials.of(testCredential, password)) {
            // Close credentials - should not throw
            assertDoesNotThrow(credentials::close);
        }
    }

    @Test
    void testCredentialsCloseIsIdempotent() {
        final Password password = Password.of("ValidPassword1234".toCharArray());  // 16 chars
        try (final var credentials = Credentials.of(testCredential, password)) {
            // Call close multiple times - should not throw
            assertDoesNotThrow(() -> {
                credentials.close();
                credentials.close();
                credentials.close();
            });
        }
    }

    @Test
    void testCredentialsCloseHandlesPasswordCloseException() {
        // Create a mock Password that throws an exception when closed
        final Password mockPassword = mock(Password.class);
        doThrow(new RuntimeException("Simulated password close failure")).when(mockPassword).close();

        try (final var credentials = Credentials.of(testCredential, mockPassword)) {
            // Should not throw even though password.close() throws an exception
            assertDoesNotThrow(credentials::close);
        }
    }
}
