package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.value.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

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
        
        final UserId userId = UserId.of(UUID.randomUUID());
        final Username username = Username.of("test_user");
        testUser = new User(userId, username, Set.of());
    }

    private void setupTokenSignerMock() {
        when(tokenSigner.sign(org.mockito.ArgumentMatchers.any(com.oodesigns.cas.domain.value.Payload.class), org.mockito.ArgumentMatchers.any(Instant.class)))
            .thenAnswer(invocation -> java.util.Optional.of("signed.%s".formatted(((com.oodesigns.cas.domain.value.Payload) invocation.getArgument(0)).value())));
    }

    @Test
    void testGenerateTokensReturnsValidTokenPair() {
        setupTokenSignerMock();
        final Instant now = Instant.now();
        when(clock.now()).thenReturn(now);

        final var tokensOptional = tokenService.generateTokens(testUser);

        assertTrue(tokensOptional.isPresent());
        final TokenService.TokenPair tokens = tokensOptional.get();
        assertNotNull(tokens.accessToken());
        assertNotNull(tokens.refreshToken());
    }

    @Test
    void testGenerateTokensNullUserReturnsEmpty() {
        final var result = tokenService.generateTokens(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGenerateTokensCreatesUniqueJti() {
        setupTokenSignerMock();
        when(clock.now()).thenReturn(Instant.now());

        final var tokens1Optional = tokenService.generateTokens(testUser);
        final var tokens2Optional = tokenService.generateTokens(testUser);
        
        assertTrue(tokens1Optional.isPresent());
        assertTrue(tokens2Optional.isPresent());
        assertNotEquals(tokens1Optional.get().accessToken(), tokens2Optional.get().accessToken());
    }

    @Test
    void testTokenPairContainsAllComponents() {
        setupTokenSignerMock();
        when(clock.now()).thenReturn(Instant.now());

        final var tokensOptional = tokenService.generateTokens(testUser);
        assertTrue(tokensOptional.isPresent());
        
        final TokenService.TokenPair tokens = tokensOptional.get();
        assertNotNull(tokens.accessToken());
        assertNotNull(tokens.refreshToken());
        assertFalse(tokens.accessToken().isEmpty());
        assertFalse(tokens.refreshToken().isEmpty());
    }
}
