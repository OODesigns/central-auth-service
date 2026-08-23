package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Jti;
import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogoutCommandHandlerTest {

    private static final String ACCESS_TOKEN = "access.token.value";

    @Mock private Ports.TokenVerifier tokenVerifier;
    @Mock private Ports.AccessTokenRevocationStore revocationStore;

    private LogoutCommandHandler handler;
    private Ports.AccessTokenClaims claims;

    @BeforeEach
    void setUp() {
        handler = new LogoutCommandHandler(tokenVerifier, revocationStore);
        claims = new Ports.AccessTokenClaims(UserId.of(UUID.randomUUID()), Jti.of(UUID.randomUUID()), Instant.now().plusSeconds(900));
    }

    @Test
    void handleReturnsSuccessAndInvalidatesTokenWhenTokenIsValid() {
        when(tokenVerifier.verifyAccessToken(ACCESS_TOKEN)).thenReturn(Optional.of(claims));

        final LogoutResult result = handler.handle(new LogoutCommand(ACCESS_TOKEN));

        result.mapTo(s -> {
            verify(revocationStore).invalidate(eq(claims), eq(ACCESS_TOKEN), eq("logout"));
            return null;
        }).orElse(f -> {
            fail("Expected success: " + f.errorCode());
            return null;
        });
    }

    @Test
    void handleReturnsInvalidAccessTokenWhenVerifierRejects() {
        when(tokenVerifier.verifyAccessToken(ACCESS_TOKEN)).thenReturn(Optional.empty());

        final LogoutResult result = handler.handle(new LogoutCommand(ACCESS_TOKEN));

        result.mapTo(s -> {
            fail("Expected failure");
            return null;
        }).orElse(f -> {
            assertEquals("INVALID_ACCESS_TOKEN", f.errorCode());
            return null;
        });

        verify(revocationStore, never()).invalidate(any(), any(), any());
    }

    @Test
    void handleReturnsInvalidRequestForNullCommand() {
        final LogoutResult result = handler.handle(null);

        result.mapTo(s -> {
            fail("Expected failure");
            return null;
        }).orElse(f -> {
            assertEquals("INVALID_REQUEST", f.errorCode());
            return null;
        });

        verifyNoInteractions(tokenVerifier, revocationStore);
    }

    @Test
    void handleReturnsInternalErrorWhenRevocationFails() {
        when(tokenVerifier.verifyAccessToken(ACCESS_TOKEN)).thenThrow(new IllegalStateException("database unavailable"));

        final LogoutResult result = handler.handle(new LogoutCommand(ACCESS_TOKEN));

        result.mapTo(success -> {
            fail("Expected failure");
            return null;
        }).orElse(failure -> {
            assertEquals("INTERNAL_ERROR", failure.errorCode());
            assertEquals("Logout could not be completed.", failure.errorMessage());
            return null;
        });
    }

    @Test
    void logoutResultRejectsBlankFailureDetails() {
        assertThrows(IllegalArgumentException.class, () -> LogoutResult.failure(null, "message"));
        assertThrows(IllegalArgumentException.class, () -> LogoutResult.failure(" ", "message"));
        assertThrows(IllegalArgumentException.class, () -> LogoutResult.failure("ERROR", null));
        assertThrows(IllegalArgumentException.class, () -> LogoutResult.failure("ERROR", " "));
    }

    @Test
    void constructorRejectsNulls() {
        assertThrows(NullPointerException.class, () -> new LogoutCommandHandler(null, revocationStore));
        assertThrows(NullPointerException.class, () -> new LogoutCommandHandler(tokenVerifier, null));
    }
}
