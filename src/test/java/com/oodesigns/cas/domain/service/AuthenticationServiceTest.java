package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.entity.User;
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
    private User testUser;

    @BeforeEach
    void setUp() {
        authService = new AuthenticationService(passwordHasher);
        
        UserId userId = UserId.generate();
        Username username = new Username("test_user");
        PasswordHash passwordHash = new PasswordHash("$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
        testUser = User.create(userId, username, passwordHash);
    }

    @Test
    void testAuthenticateValidPassword() {
        Password password = new Password("password123".toCharArray());
        var credentials = new Credentials(testUser, password);
        when(passwordHasher.verify(credentials)).thenReturn(java.util.Optional.of(testUser));

        var result = authService.getAuthenticatedUser(credentials);

        assertTrue(result.isPresent());
        assertEquals(testUser, result.get());
        verify(passwordHasher).verify(credentials);
    }

    @Test
    void testAuthenticateInvalidPassword() {
        Password password = new Password("wrongpassword".toCharArray());
        var credentials = new Credentials(testUser, password);
        when(passwordHasher.verify(credentials)).thenReturn(java.util.Optional.empty());

        var result = authService.getAuthenticatedUser(credentials);

        assertTrue(result.isEmpty());
        verify(passwordHasher).verify(credentials);
    }

    @Test
    void testAuthenticateNullCredentialsThrowsNullPointerException() {
        Password password = new Password("password".toCharArray());
        assertThrows(NullPointerException.class, () -> new Credentials(null, password));
    }

    @Test
    void testAuthenticateNullPasswordThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new Credentials(testUser, null));
    }
}
