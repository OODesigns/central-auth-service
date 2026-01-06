package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.value.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationService domain service.
 * Validates: password verification logic.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private Ports.PasswordVerifier passwordHasher;
    
    private AuthenticationService authService;
    private UserCredential testCredential;

    @BeforeEach
    void setUp() {
        authService = new AuthenticationService(passwordHasher);
        
        final UserId userId = UserId.generate();
        final PasswordHash passwordHash = new PasswordHash("$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
        testCredential = new UserCredential(userId, passwordHash);
    }

    @Test
    void testConstructorWithNullPasswordVerifierThrows() {
        assertThrows(NullPointerException.class, () -> new AuthenticationService(null));
    }

    @Test
    void testAuthenticateValidPassword() {
        final Password password = new Password("password123".toCharArray());
        try (final var credentials = new Credentials(testCredential, password)) {
            when(passwordHasher.verify(credentials)).thenReturn(java.util.Optional.of(testCredential.userId()));

            final var result = authService.getAuthenticatedUser(credentials);

            assertTrue(result.isPresent());
            assertEquals(testCredential.userId(), result.get());
            verify(passwordHasher).verify(credentials);
        }
    }

    @Test
    void testAuthenticateInvalidPassword() {
        final Password password = new Password("wrong_password".toCharArray());
        try (final var credentials = new Credentials(testCredential, password)) {
            when(passwordHasher.verify(credentials)).thenReturn(java.util.Optional.empty());

            final var result = authService.getAuthenticatedUser(credentials);

            assertTrue(result.isEmpty());
            verify(passwordHasher).verify(credentials);
        }
    }

    @Test
    void testAuthenticateNullCredentialThrowsNullPointerException() {
        final Password password = new Password("password".toCharArray());
        assertThrows(NullPointerException.class, () -> createCredentialsAndClose(null, password));
    }

    @Test
    void testAuthenticateNullPasswordThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> createCredentialsAndClose(testCredential, null));
    }

    /**
     * Helper that constructs and immediately closes Credentials, allowing constructor validation to throw.
     */
    private void createCredentialsAndClose(final UserCredential credential, final Password password) {
        try (final Credentials credentials = new Credentials(credential, password)) {
            java.util.Objects.requireNonNull(credentials); // touch to satisfy analysis; construction is what we validate
        }
    }

    @Test
    void testAuthenticateClosesCredentialsAfterVerification() {
        final char[] passwordChars = "password123".toCharArray();
        final Password password = new Password(passwordChars);
        final Credentials credentials = new Credentials(testCredential, password);
        when(passwordHasher.verify(credentials)).thenReturn(java.util.Optional.of(testCredential.userId()));

        authService.getAuthenticatedUser(credentials);

        // After getAuthenticatedUser, the password should be cleared (close() was called)
        // Verify by checking that chars() returns zeroed array
        final char[] clearedChars = password.chars();
        for (final char c : clearedChars) {
            assertEquals('\0', c, "Password should be cleared after authentication");
        }
    }

    @Test
    void testAuthenticateClosesCredentialsEvenWhenVerificationFails() {
        final char[] passwordChars = "wrong_password".toCharArray();
        final Password password = new Password(passwordChars);
        final Credentials credentials = new Credentials(testCredential, password);
        when(passwordHasher.verify(credentials)).thenReturn(java.util.Optional.empty());

        authService.getAuthenticatedUser(credentials);

        // After getAuthenticatedUser, the password should be cleared even on failure
        final char[] clearedChars = password.chars();
        for (final char c : clearedChars) {
            assertEquals('\0', c, "Password should be cleared after failed authentication");
        }
    }

    @Test
    void testAuthenticateClosesCredentialsWhenVerifierThrows() {
        final char[] passwordChars = "password123".toCharArray();
        final Password password = new Password(passwordChars);
        final Credentials credentials = new Credentials(testCredential, password);
        when(passwordHasher.verify(credentials)).thenThrow(new RuntimeException("Verifier error"));

        assertThrows(RuntimeException.class, () -> authService.getAuthenticatedUser(credentials));

        // After exception, the password should still be cleared (try-with-resources)
        final char[] clearedChars = password.chars();
        for (final char c : clearedChars) {
            assertEquals('\0', c, "Password should be cleared after exception");
        }
    }
}
