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
        
        UserId userId = UserId.generate();
        PasswordHash passwordHash = new PasswordHash("$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
        testCredential = new UserCredential(userId, passwordHash);
    }

    @Test
    void testAuthenticateValidPassword() {
        Password password = new Password("password123".toCharArray());
        try (var credentials = new Credentials(testCredential, password)) {
            when(passwordHasher.verify(credentials)).thenReturn(java.util.Optional.of(testCredential.userId()));

            var result = authService.getAuthenticatedUser(credentials);

            assertTrue(result.isPresent());
            assertEquals(testCredential.userId(), result.get());
            verify(passwordHasher).verify(credentials);
        }
    }

    @Test
    void testAuthenticateInvalidPassword() {
        Password password = new Password("wrong_password".toCharArray());
        try (var credentials = new Credentials(testCredential, password)) {
            when(passwordHasher.verify(credentials)).thenReturn(java.util.Optional.empty());

            var result = authService.getAuthenticatedUser(credentials);

            assertTrue(result.isEmpty());
            verify(passwordHasher).verify(credentials);
        }
    }

    @Test
    void testAuthenticateNullCredentialThrowsNullPointerException() {
        Password password = new Password("password".toCharArray());
        assertThrows(NullPointerException.class, () -> createCredentialsAndClose(null, password));
    }

    @Test
    void testAuthenticateNullPasswordThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> createCredentialsAndClose(testCredential, null));
    }

    /**
     * Helper that constructs and immediately closes Credentials, allowing constructor validation to throw.
     */
    private void createCredentialsAndClose(UserCredential credential, Password password) {
        try (Credentials credentials = new Credentials(credential, password)) {
            java.util.Objects.requireNonNull(credentials); // touch to satisfy analysis; construction is what we validate
        }
    }
}
