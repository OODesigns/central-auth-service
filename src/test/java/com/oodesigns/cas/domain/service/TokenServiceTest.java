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
        testUser = new User(userId, username, Set.of(), null, null);
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

    @Test
    void testGenerate2FAVerificationToken() {
        when(clock.now()).thenReturn(Instant.ofEpochSecond(1_700_000_000L));
        // capture the payload passed to signer
        final java.util.concurrent.atomic.AtomicReference<com.oodesigns.cas.domain.value.Payload> captured = new java.util.concurrent.atomic.AtomicReference<>();
        when(tokenSigner.sign(org.mockito.ArgumentMatchers.any(com.oodesigns.cas.domain.value.Payload.class), org.mockito.ArgumentMatchers.any(Instant.class)))
            .thenAnswer(invocation -> {
                final com.oodesigns.cas.domain.value.Payload p = invocation.getArgument(0);
                captured.set(p);
                return java.util.Optional.of("signed-token");
            });

        final com.oodesigns.cas.domain.value.UserId uid = com.oodesigns.cas.domain.value.UserId.of(java.util.UUID.randomUUID());
        final String token = tokenService.generate2FAVerificationToken(uid);
        assertNotNull(token);
        assertEquals("signed-token", token);

        final com.oodesigns.cas.domain.value.Payload payload = captured.get();
        assertNotNull(payload);
        final String json = payload.value();
        assertTrue(json.contains("\"aud\":\"2fa_verification\""));
        assertTrue(json.contains("\"sub\":\"" + uid.toString() + "\""));
        // exp should be iat + 300 seconds (5 minutes)
        // iat was 1_700_000_000
        assertTrue(json.contains("\"iat\":1700000000"));
        assertTrue(json.contains("\"exp\":1700000300"));
    }

    @Test
    void testGenerate2FAVerificationTokenSignerFailureThrows() {
        when(clock.now()).thenReturn(Instant.now());
        when(tokenSigner.sign(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.Optional.empty());

        final com.oodesigns.cas.domain.value.UserId uid = com.oodesigns.cas.domain.value.UserId.of(java.util.UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> tokenService.generate2FAVerificationToken(uid));
    }

    @Test
    void testGenerateMfaEnrollmentToken() {
        when(clock.now()).thenReturn(Instant.ofEpochSecond(1_700_000_000L));
        when(tokenSigner.sign(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.Optional.of("enrollment-token"));

        final com.oodesigns.cas.domain.value.UserId uid = com.oodesigns.cas.domain.value.UserId.of(java.util.UUID.randomUUID());
        assertEquals("enrollment-token", tokenService.generateMfaEnrollmentToken(uid));
    }

    @Test
    void testGenerateMfaEnrollmentTokenSignerFailureThrows() {
        when(clock.now()).thenReturn(Instant.now());
        when(tokenSigner.sign(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.Optional.empty());

        final com.oodesigns.cas.domain.value.UserId uid = com.oodesigns.cas.domain.value.UserId.of(java.util.UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> tokenService.generateMfaEnrollmentToken(uid));
    }
}
