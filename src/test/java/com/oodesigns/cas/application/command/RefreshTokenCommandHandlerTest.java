package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCommandHandlerTest {

    private static final String REFRESH_TOKEN = "presented.refresh.token";

    @Mock private Ports.TokenVerifier tokenVerifier;
    @Mock private Ports.UserRetriever userRetriever;
    @Mock private Ports.TokenSigner tokenSigner;
    @Mock private Ports.Clock clock;
    @Mock private Ports.RefreshTokenStore refreshTokenStore;

    private RefreshTokenCommandHandler handler;
    private TokenService tokenService;
    private UserId userId;
    private User user;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(clock, tokenSigner);
        handler = new RefreshTokenCommandHandler(tokenVerifier, userRetriever, tokenService, refreshTokenStore);
        userId = UserId.of(UUID.randomUUID());
        user = new User(userId, Username.of("alice"), Set.of(Permission.of("read")), null, null);
    }

    private void mockTokenGeneration() {
        when(clock.now()).thenReturn(Instant.now());
        when(tokenSigner.sign(any(), any())).thenReturn(Optional.of("signed.token"));
    }

    @Test
    void handleReturnsSuccessAndRotatesWhenTokenIsCurrent() {
        when(tokenVerifier.verifyRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.of(userId));
        when(userRetriever.findById(userId)).thenReturn(Optional.of(user));
        mockTokenGeneration();
        when(refreshTokenStore.rotate(eq(REFRESH_TOKEN), anyString()))
            .thenReturn(Ports.RefreshTokenStore.RotationStatus.ROTATED);

        final RefreshTokenResult result = handler.handle(new RefreshTokenCommand(REFRESH_TOKEN));

        result.mapTo(s -> {
            assertEquals(userId, s.userId());
            return null;
        }).orElse(f -> { fail("Expected success: " + f.errorCode()); return null; });

        verify(refreshTokenStore).rotate(eq(REFRESH_TOKEN), anyString());
    }

    @Test
    void handleReturnsReuseDetectedWhenStoreDetectsReplay() {
        when(tokenVerifier.verifyRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.of(userId));
        when(userRetriever.findById(userId)).thenReturn(Optional.of(user));
        mockTokenGeneration();
        when(refreshTokenStore.rotate(eq(REFRESH_TOKEN), anyString()))
            .thenReturn(Ports.RefreshTokenStore.RotationStatus.REUSE_DETECTED);

        final RefreshTokenResult result = handler.handle(new RefreshTokenCommand(REFRESH_TOKEN));

        result.mapTo(s -> { fail("Expected reuse detection"); return null; })
            .orElse(f -> { assertEquals("REFRESH_TOKEN_REUSE_DETECTED", f.errorCode()); return null; });
    }

    @Test
    void handleReturnsExpiredWhenStoredTokenExpired() {
        when(tokenVerifier.verifyRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.of(userId));
        when(userRetriever.findById(userId)).thenReturn(Optional.of(user));
        mockTokenGeneration();
        when(refreshTokenStore.rotate(eq(REFRESH_TOKEN), anyString()))
            .thenReturn(Ports.RefreshTokenStore.RotationStatus.EXPIRED);

        final RefreshTokenResult result = handler.handle(new RefreshTokenCommand(REFRESH_TOKEN));

        result.mapTo(s -> { fail("Expected expired"); return null; })
            .orElse(f -> { assertEquals("REFRESH_TOKEN_EXPIRED", f.errorCode()); return null; });
    }

    @Test
    void handleReturnsInvalidRefreshTokenWhenStoreReportsNotFound() {
        when(tokenVerifier.verifyRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.of(userId));
        when(userRetriever.findById(userId)).thenReturn(Optional.of(user));
        mockTokenGeneration();
        when(refreshTokenStore.rotate(eq(REFRESH_TOKEN), anyString()))
            .thenReturn(Ports.RefreshTokenStore.RotationStatus.NOT_FOUND);

        final RefreshTokenResult result = handler.handle(new RefreshTokenCommand(REFRESH_TOKEN));

        result.mapTo(s -> { fail("Expected invalid refresh token"); return null; })
            .orElse(f -> { assertEquals("INVALID_REFRESH_TOKEN", f.errorCode()); return null; });
    }

    @Test
    void handleReturnsInvalidRefreshTokenWhenVerifierRejects() {
        when(tokenVerifier.verifyRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.empty());

        final RefreshTokenResult result = handler.handle(new RefreshTokenCommand(REFRESH_TOKEN));

        result.mapTo(s -> { fail("Expected invalid refresh token"); return null; })
            .orElse(f -> { assertEquals("INVALID_REFRESH_TOKEN", f.errorCode()); return null; });

        verifyNoInteractions(userRetriever, refreshTokenStore);
    }

    @Test
    void handleReturnsUserNotFoundWhenUserMissing() {
        when(tokenVerifier.verifyRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.of(userId));
        when(userRetriever.findById(userId)).thenReturn(Optional.empty());

        final RefreshTokenResult result = handler.handle(new RefreshTokenCommand(REFRESH_TOKEN));

        result.mapTo(s -> { fail("Expected user not found"); return null; })
            .orElse(f -> { assertEquals("USER_NOT_FOUND", f.errorCode()); return null; });

        verifyNoInteractions(refreshTokenStore);
    }

    @Test
    void handleReturnsInternalErrorWhenTokenSigningFails() {
        when(tokenVerifier.verifyRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.of(userId));
        when(userRetriever.findById(userId)).thenReturn(Optional.of(user));
        when(clock.now()).thenReturn(Instant.now());
        when(tokenSigner.sign(any(), any())).thenReturn(Optional.empty());

        final RefreshTokenResult result = handler.handle(new RefreshTokenCommand(REFRESH_TOKEN));

        result.mapTo(s -> { fail("Expected internal error"); return null; })
            .orElse(f -> { assertEquals("INTERNAL_ERROR", f.errorCode()); return null; });

        // No rotation should be attempted if the replacement token could not be generated.
        verify(refreshTokenStore, never()).rotate(anyString(), anyString());
    }

    @Test
    void handleReturnsInvalidRequestForNullCommand() {
        final RefreshTokenResult result = handler.handle(null);

        result.mapTo(s -> { fail("Expected invalid request"); return null; })
            .orElse(f -> { assertEquals("INVALID_REQUEST", f.errorCode()); return null; });

        verifyNoInteractions(tokenVerifier, userRetriever, refreshTokenStore);
    }

    @Test
    void handleReturnsInternalErrorWhenVerifierThrows() {
        when(tokenVerifier.verifyRefreshToken(any())).thenThrow(new RuntimeException("db down"));

        final Logger logger = Logger.getLogger(RefreshTokenCommandHandler.class.getName());
        final Level old = logger.getLevel();
        try {
            logger.setLevel(Level.OFF);
            final RefreshTokenResult result = handler.handle(new RefreshTokenCommand(REFRESH_TOKEN));
            result.mapTo(s -> { fail("Expected internal error"); return null; })
                .orElse(f -> { assertEquals("INTERNAL_ERROR", f.errorCode()); return null; });
        } finally {
            logger.setLevel(old);
        }
    }

    @Test
    void constructorRejectsNulls() {
        assertThrows(NullPointerException.class,
            () -> new RefreshTokenCommandHandler(null, userRetriever, tokenService, refreshTokenStore));
        assertThrows(NullPointerException.class,
            () -> new RefreshTokenCommandHandler(tokenVerifier, null, tokenService, refreshTokenStore));
        assertThrows(NullPointerException.class,
            () -> new RefreshTokenCommandHandler(tokenVerifier, userRetriever, null, refreshTokenStore));
        assertThrows(NullPointerException.class,
            () -> new RefreshTokenCommandHandler(tokenVerifier, userRetriever, tokenService, null));
    }
}

