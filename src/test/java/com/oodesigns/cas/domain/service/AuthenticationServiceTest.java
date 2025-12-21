package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.value.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationService domain service.
 * Validates: authentication logic, token generation, port usage.
 */
@ExtendWith(MockitoExtension.class)
public class AuthenticationServiceTest {

    @Mock
    private Ports.PasswordHasher passwordHasher;

    @Mock
    private Ports.Clock clock;

    private AuthenticationService authService;
    private User testUser;

    @BeforeEach
    public void setUp() {
        authService = new AuthenticationService(passwordHasher, clock);
        
        UserId userId = UserId.generate();
        Username username = new Username("test_user");
        PasswordHash passwordHash = new PasswordHash("$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
        testUser = User.create(userId, username, passwordHash);
    }

    @Test
    public void testAuthenticateValidPassword() {
        when(passwordHasher.verify("password123", testUser.getPasswordHash())).thenReturn(true);

        AuthenticationService.AuthenticationResult result = authService.authenticate(testUser, "password123");

        assertTrue(result.isSuccess());
        assertEquals(testUser, result.getUser());
        verify(passwordHasher).verify("password123", testUser.getPasswordHash());
    }

    @Test
    public void testAuthenticateInvalidPassword() {
        when(passwordHasher.verify("wrongpassword", testUser.getPasswordHash())).thenReturn(false);

        AuthenticationService.AuthenticationResult result = authService.authenticate(testUser, "wrongpassword");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Invalid password"));
        verify(passwordHasher).verify("wrongpassword", testUser.getPasswordHash());
    }

    @Test
    public void testAuthenticateNullUserReturnsFailedResult() {
        // Null user is allowed and returns failed result
        AuthenticationService.AuthenticationResult result = authService.authenticate(null, "password");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("User not found"));
    }

    @Test
    public void testAuthenticateNullPasswordThrows() {
        assertThrows(NullPointerException.class, 
            () -> authService.authenticate(testUser, null));
    }

    @Test
    public void testGenerateTokensReturnsValidTokenPair() {
        Instant now = Instant.now();
        when(clock.now()).thenReturn(now);

        AuthenticationService.TokenPair tokens = authService.generateTokens(testUser);

        assertNotNull(tokens.getAccessToken());
        assertNotNull(tokens.getRefreshToken());
        assertNotNull(tokens.getJti());
    }

    @Test
    public void testGenerateTokensNullUserThrows() {
        assertThrows(NullPointerException.class, 
            () -> authService.generateTokens(null));
    }

    @Test
    public void testGenerateTokensCreatesUniqueJti() {
        when(clock.now()).thenReturn(Instant.now());

        AuthenticationService.TokenPair tokens1 = authService.generateTokens(testUser);
        AuthenticationService.TokenPair tokens2 = authService.generateTokens(testUser);

        assertNotEquals(tokens1.getJti(), tokens2.getJti());
    }

    @Test
    public void testAuthenticationResultSuccessState() {
        AuthenticationService.AuthenticationResult result = AuthenticationService.AuthenticationResult.success(testUser);

        assertTrue(result.isSuccess());
        assertEquals(testUser, result.getUser());
    }

    @Test
    public void testAuthenticationResultFailureState() {
        String errorMsg = "Authentication failed";
        AuthenticationService.AuthenticationResult result = AuthenticationService.AuthenticationResult.failed(errorMsg);

        assertFalse(result.isSuccess());
        assertEquals(errorMsg, result.getErrorMessage());
    }

    @Test
    public void testAccessingSuccessResultErrorThrows() {
        AuthenticationService.AuthenticationResult result = AuthenticationService.AuthenticationResult.success(testUser);

        assertThrows(IllegalStateException.class, result::getErrorMessage);
    }

    @Test
    public void testAccessingFailureResultUserThrows() {
        AuthenticationService.AuthenticationResult result = AuthenticationService.AuthenticationResult.failed("error");

        assertThrows(IllegalStateException.class, result::getUser);
    }

    @Test
    public void testTokenPairContainsAllComponents() {
        when(clock.now()).thenReturn(Instant.now());

        AuthenticationService.TokenPair tokens = authService.generateTokens(testUser);

        assertNotNull(tokens.getAccessToken());
        assertNotNull(tokens.getRefreshToken());
        assertNotNull(tokens.getJti());
        assertTrue(tokens.getAccessToken().length() > 0);
        assertTrue(tokens.getRefreshToken().length() > 0);
    }
}
