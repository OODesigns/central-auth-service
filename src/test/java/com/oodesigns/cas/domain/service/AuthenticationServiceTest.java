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
import static org.mockito.ArgumentMatchers.*;

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

    @Mock
    private Ports.TokenSigner tokenSigner;

    private AuthenticationService authService;
    private User testUser;

    @BeforeEach
    public void setUp() {
        authService = new AuthenticationService(passwordHasher, clock, tokenSigner);
        
        // Mock token signer to return simple signed tokens for testing
        when(tokenSigner.sign(anyString(), any(Instant.class)))
            .thenAnswer(invocation -> "signed." + invocation.getArgument(0));
        
        UserId userId = UserId.generate();
        Username username = new Username("test_user");
        PasswordHash passwordHash = new PasswordHash("$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
        testUser = User.create(userId, username, passwordHash);
    }

    @Test
    public void testAuthenticateValidPassword() {
        when(passwordHasher.verify("password123", testUser.passwordHash())).thenReturn(true);

        var result = authService.getAuthenticatedUser(testUser, "password123".toCharArray());

        assertTrue(result.isPresent());
        assertEquals(testUser, result.get());
        verify(passwordHasher).verify("password123", testUser.passwordHash());
    }

    @Test
    public void testAuthenticateInvalidPassword() {
        when(passwordHasher.verify("wrongpassword", testUser.passwordHash())).thenReturn(false);

        var result = authService.getAuthenticatedUser(testUser, "wrongpassword".toCharArray());

        assertTrue(result.isEmpty());
        verify(passwordHasher).verify("wrongpassword", testUser.passwordHash());
    }

    @Test
    public void testAuthenticateNullUserReturnsEmpty() {
        // Null user returns empty Optional
        var result = authService.getAuthenticatedUser(null, "password".toCharArray());
        assertTrue(result.isEmpty());
    }

    @Test
    public void testAuthenticateNullPasswordThrows() {
        assertThrows(NullPointerException.class, 
            () -> authService.getAuthenticatedUser(testUser, null));
    }

    @Test
    public void testGenerateTokensReturnsValidTokenPair() {
        Instant now = Instant.now();
        when(clock.now()).thenReturn(now);

        var tokensOptional = authService.generateTokens(testUser);
        
        assertTrue(tokensOptional.isPresent());
        AuthenticationService.TokenPair tokens = tokensOptional.get();
        assertNotNull(tokens.getAccessToken());
        assertNotNull(tokens.getRefreshToken());
        assertNotNull(tokens.getJti());
    }

    @Test
    public void testGenerateTokensNullUserReturnsEmpty() {
        var result = authService.generateTokens(null);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGenerateTokensCreatesUniqueJti() {
        when(clock.now()).thenReturn(Instant.now());

        var tokens1Optional = authService.generateTokens(testUser);
        var tokens2Optional = authService.generateTokens(testUser);
        
        assertTrue(tokens1Optional.isPresent());
        assertTrue(tokens2Optional.isPresent());
        assertNotEquals(tokens1Optional.get().getJti(), tokens2Optional.get().getJti());
    }

    @Test
    public void testTokenPairContainsAllComponents() {
        when(clock.now()).thenReturn(Instant.now());

        var tokensOptional = authService.generateTokens(testUser);
        assertTrue(tokensOptional.isPresent());
        
        AuthenticationService.TokenPair tokens = tokensOptional.get();
        assertNotNull(tokens.getAccessToken());
        assertNotNull(tokens.getRefreshToken());
        assertNotNull(tokens.getJti());
        assertTrue(tokens.getAccessToken().length() > 0);
        assertTrue(tokens.getRefreshToken().length() > 0);
    }
}
