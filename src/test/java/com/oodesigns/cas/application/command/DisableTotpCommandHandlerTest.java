package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.Ports;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisableTotpCommandHandlerTest {

    @Mock
    private Ports.UserCredentialRetriever credentialReader;

    @Mock
    private Ports.TotpSetupProvider totpSetupProvider;

    private AuthenticationService authService;
    private DisableTotpCommandHandler handler;

    @BeforeEach
    void setUp() {
        authService = new AuthenticationService(cmd -> null);
        handler = new DisableTotpCommandHandler(authService, credentialReader, totpSetupProvider);
    }

    @Test
    void constructorRejectsNulls() {
        assertThrows(NullPointerException.class, () -> new DisableTotpCommandHandler(null, credentialReader, totpSetupProvider));
        assertThrows(NullPointerException.class, () -> new DisableTotpCommandHandler(authService, null, totpSetupProvider));
        assertThrows(NullPointerException.class, () -> new DisableTotpCommandHandler(authService, credentialReader, null));
    }

    @Test
    void handleReturnsSuccessWhenDisabled() {
        final var userId = com.oodesigns.cas.domain.value.UserId.of(UUID.randomUUID());
        final DisableTotpCommand cmd = new DisableTotpCommand(userId, "password123", DisableReason.USER_REQUESTED);
        when(totpSetupProvider.disableTotp(eq(userId), any())).thenReturn(true);

        // ensure INFO logging branch is exercised
        final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(DisableTotpCommandHandler.class.getName());
        final java.util.logging.Level old = logger.getLevel();
        try {
            logger.setLevel(java.util.logging.Level.INFO);
            final DisableTotpResult res = handler.handle(cmd);
            res.mapTo(s -> {
                assertNotNull(s);
                return null;
            }).orElse(f -> fail("Expected success"));
        } finally {
            logger.setLevel(old);
        }
    }

    @Test
    void handleReturnsFailureWhenNotEnabled() {
        final var userId = com.oodesigns.cas.domain.value.UserId.of(UUID.randomUUID());
        final DisableTotpCommand cmd = new DisableTotpCommand(userId, "password123", DisableReason.USER_REQUESTED);

        when(totpSetupProvider.disableTotp(eq(userId), any())).thenReturn(false);

        final DisableTotpResult res = handler.handle(cmd);
        res.mapTo(s -> {
            fail("Expected failure");
            return null;
        }).orElse(f -> {
            assertEquals("TOTP_NOT_ENABLED", f.errorCode());
            return null;
        });
    }

    @Test
    void handleReturnsInternalOnException() {
        final var userId = com.oodesigns.cas.domain.value.UserId.of(UUID.randomUUID());
        final DisableTotpCommand cmd = new DisableTotpCommand(userId, "password123", DisableReason.USER_REQUESTED);

        when(totpSetupProvider.disableTotp(eq(userId), any())).thenThrow(new RuntimeException("boom"));

        final DisableTotpResult res = handler.handle(cmd);
        res.mapTo(s -> {
            fail("Expected failure");
            return null;
        }).orElse(f -> {
            assertEquals("INTERNAL_ERROR", f.errorCode());
            return null;
        });
    }

    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    @Test
    void verifyPasswordForDisableRejectsBlankPasswordViaReflection() throws Exception {
        // Create a mock DisableTotpCommand that returns a blank password
        final DisableTotpCommand mockCmd = org.mockito.Mockito.mock(DisableTotpCommand.class);
        final var userId = com.oodesigns.cas.domain.value.UserId.of(java.util.UUID.randomUUID());
        org.mockito.Mockito.when(mockCmd.userId()).thenReturn(userId);
        org.mockito.Mockito.when(mockCmd.password()).thenReturn("");
        org.mockito.Mockito.when(mockCmd.reason()).thenReturn(DisableReason.USER_REQUESTED);

        final java.lang.reflect.Method m = DisableTotpCommandHandler.class.getDeclaredMethod("verifyPasswordForDisable", DisableTotpCommand.class);
        m.setAccessible(true);
        final DisableTotpResult res = (DisableTotpResult) m.invoke(handler, mockCmd);
        res.mapTo(s -> {
            fail("Expected failure for blank password");
            return null;
        }).orElse(f -> {
            assertEquals("INVALID_PASSWORD", f.errorCode());
            return null;
        });
    }

    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    @Test
    void disableTotpForUserReturnsFailureWhenPasswordInvalidViaReflection() throws Exception {
        final DisableTotpCommand mockCmd = org.mockito.Mockito.mock(DisableTotpCommand.class);
        final var userId = com.oodesigns.cas.domain.value.UserId.of(java.util.UUID.randomUUID());
        org.mockito.Mockito.when(mockCmd.userId()).thenReturn(userId);
        org.mockito.Mockito.when(mockCmd.password()).thenReturn("");
        org.mockito.Mockito.when(mockCmd.reason()).thenReturn(DisableReason.USER_REQUESTED);

        final java.lang.reflect.Method m = DisableTotpCommandHandler.class.getDeclaredMethod("disableTotpForUser", DisableTotpCommand.class);
        m.setAccessible(true);
        final DisableTotpResult res = (DisableTotpResult) m.invoke(handler, mockCmd);
        res.mapTo(s -> {
            fail("Expected failure due to invalid password");
            return null;
        }).orElse(f -> {
            assertEquals("INVALID_PASSWORD", f.errorCode());
            return null;
        });
    }
}

