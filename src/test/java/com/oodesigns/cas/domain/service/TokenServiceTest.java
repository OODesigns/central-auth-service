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
 * Unit tests for TokenService domain service.
 * Validates: token generation logic, JTI uniqueness, token structure.
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private Ports.Clock clock;

    @Mock
    private Ports.TokenSigner tokenSigner;
    
    private TokenService tokenService;
    private User testUser;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(clock, tokenSigner);
        
        UserId userId = UserId.generate();
        Username username = new Username("test_user");
        PasswordHash passwordHash = new PasswordHash("$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
        testUser = User.create(userId, username, passwordHash);
    }

    private void setupTokenSignerMock() {
        when(tokenSigner.sign(org.mockito.ArgumentMatchers.any(com.oodesigns.cas.domain.value.Payload.class), org.mockito.ArgumentMatchers.any(Instant.class)))
            .thenAnswer(invocation -> java.util.Optional.of("signed." + ((com.oodesigns.cas.domain.value.Payload) invocation.getArgument(0)).value()));
    }

    @Test
    void testGenerateTokensReturnsValidTokenPair() {
        setupTokenSignerMock();
        Instant now = Instant.now();
        when(clock.now()).thenReturn(now);

        var tokensOptional = tokenService.generateTokens(testUser);

        assertTrue(tokensOptional.isPresent());
        TokenService.TokenPair tokens = tokensOptional.get();
        assertNotNull(tokens.getAccessToken());
        assertNotNull(tokens.getRefreshToken());
        assertNotNull(tokens.getJti());
    }

    @Test
    void testGenerateTokensNullUserReturnsEmpty() {
        var result = tokenService.generateTokens(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGenerateTokensCreatesUniqueJti() {
        setupTokenSignerMock();
        when(clock.now()).thenReturn(Instant.now());

        var tokens1Optional = tokenService.generateTokens(testUser);
        var tokens2Optional = tokenService.generateTokens(testUser);
        
        assertTrue(tokens1Optional.isPresent());
        assertTrue(tokens2Optional.isPresent());
        assertNotEquals(tokens1Optional.get().getJti(), tokens2Optional.get().getJti());
    }

    @Test
    void testTokenPairContainsAllComponents() {
        setupTokenSignerMock();
        when(clock.now()).thenReturn(Instant.now());

        var tokensOptional = tokenService.generateTokens(testUser);
        assertTrue(tokensOptional.isPresent());
        
        TokenService.TokenPair tokens = tokensOptional.get();
        assertNotNull(tokens.getAccessToken());
        assertNotNull(tokens.getRefreshToken());
        assertNotNull(tokens.getJti());
        assertFalse(tokens.getAccessToken().isEmpty());
        assertFalse(tokens.getRefreshToken().isEmpty());
    }
}
