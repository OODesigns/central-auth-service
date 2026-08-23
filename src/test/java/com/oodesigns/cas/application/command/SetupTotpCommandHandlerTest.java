package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SetupTotpCommandHandler}.
 * <p>
 * Validates the setup flow: secret generation, {@code otpauth://} URI construction, null
 * handling, exception wrapping, and constructor guards.
 */
@ExtendWith(MockitoExtension.class)
class SetupTotpCommandHandlerTest {

    private static final String RFC6238_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final String ISSUER = "CentralAuthService";

    @Mock
    private Ports.TotpSetupProvider totpSetupProvider;

    private SetupTotpCommandHandler handler;
    private UserId userId;
    private SetupTotpCommand command;

    @BeforeEach
    void setUp() {
        handler = new SetupTotpCommandHandler(totpSetupProvider, ISSUER);
        userId = UserId.of(UUID.randomUUID());
        command = new SetupTotpCommand(userId, Username.of("alice"));
    }

    // ---------------------------------------------------------------- happy path

    @Test
    void handleReturnsSuccessWithSecretAndUri() {
        when(totpSetupProvider.generateSecret(userId)).thenReturn(RFC6238_SECRET);

        final SetupTotpResult result = handler.handle(command);

        result.mapTo(success -> {
            assertEquals(RFC6238_SECRET, success.secret());
            assertTrue(success.otpauthUri().startsWith("otpauth://totp/"),
                "URI must start with otpauth://totp/");
            return null;
        }).orElse(f -> {
            fail("Expected success but got failure: " + f.errorCode());
            return null;
        });
    }

    @Test
    void handleGeneratedUriContainsSecretParameter() {
        when(totpSetupProvider.generateSecret(userId)).thenReturn(RFC6238_SECRET);

        final SetupTotpResult result = handler.handle(command);

        result.mapTo(success -> {
            assertTrue(success.otpauthUri().contains("secret=" + RFC6238_SECRET));
            return null;
        }).orElse(f -> { fail("Expected success"); return null; });
    }

    @Test
    void handleGeneratedUriContainsIssuerParameter() {
        when(totpSetupProvider.generateSecret(userId)).thenReturn(RFC6238_SECRET);

        final SetupTotpResult result = handler.handle(command);

        result.mapTo(success -> {
            assertTrue(success.otpauthUri().contains("issuer=" + ISSUER),
                "URI must contain issuer parameter: " + success.otpauthUri());
            return null;
        }).orElse(f -> { fail("Expected success"); return null; });
    }

    @Test
    void handleGeneratedUriContainsAccountInLabel() {
        when(totpSetupProvider.generateSecret(userId)).thenReturn(RFC6238_SECRET);

        final SetupTotpResult result = handler.handle(command);

        result.mapTo(success -> {
            // Label format: issuer:account — both in the path segment after "otpauth://totp/"
            assertTrue(success.otpauthUri().contains(ISSUER + ":alice"),
                "URI label must be issuer:account. URI=" + success.otpauthUri());
            return null;
        }).orElse(f -> { fail("Expected success"); return null; });
    }

    @Test
    void handleGeneratedUriContainsAlgorithmDigitsPeriod() {
        when(totpSetupProvider.generateSecret(userId)).thenReturn(RFC6238_SECRET);

        final SetupTotpResult result = handler.handle(command);

        result.mapTo(success -> {
            final String uri = success.otpauthUri();
            assertTrue(uri.contains("algorithm=SHA1"), uri);
            assertTrue(uri.contains("digits=6"), uri);
            assertTrue(uri.contains("period=30"), uri);
            return null;
        }).orElse(f -> { fail("Expected success"); return null; });
    }

    @Test
    void handleEncodesSpecialCharactersInIssuerAndAccount() {
        final SetupTotpCommandHandler specialHandler =
            new SetupTotpCommandHandler(totpSetupProvider, "My Auth Service");
        final SetupTotpCommand specialCmd =
            new SetupTotpCommand(userId, Username.of("alice"));
        when(totpSetupProvider.generateSecret(userId)).thenReturn(RFC6238_SECRET);

        final SetupTotpResult result = specialHandler.handle(specialCmd);

        result.mapTo(success -> {
            // Spaces should be %20 (not +) in the URI
            assertFalse(success.otpauthUri().contains("+"),
                "URI must not contain '+'; spaces should be encoded as %20. URI=" + success.otpauthUri());
            assertTrue(success.otpauthUri().contains("My%20Auth%20Service"),
                "URI must URL-encode spaces as %20. URI=" + success.otpauthUri());
            return null;
        }).orElse(f -> { fail("Expected success"); return null; });
    }

    @Test
    void handleCallsGenerateSecretWithCorrectUserId() {
        when(totpSetupProvider.generateSecret(userId)).thenReturn(RFC6238_SECRET);
        handler.handle(command);
        verify(totpSetupProvider).generateSecret(userId);
    }

    // ---------------------------------------------------------------- null/exception handling

    @Test
    void handleReturnsInvalidRequestForNullCommand() {
        final SetupTotpResult result = handler.handle(null);

        result.mapTo(s -> { fail("Expected failure for null command"); return null; })
            .orElse(f -> {
                assertEquals("INVALID_REQUEST", f.errorCode());
                return null;
            });

        verifyNoInteractions(totpSetupProvider);
    }

    @Test
    void handleReturnsInternalErrorWhenProviderThrows() {
        when(totpSetupProvider.generateSecret(any())).thenThrow(new RuntimeException("db down"));

        final Logger logger = Logger.getLogger(SetupTotpCommandHandler.class.getName());
        final Level old = logger.getLevel();
        try {
            logger.setLevel(Level.OFF);
            final SetupTotpResult result = handler.handle(command);

            result.mapTo(s -> { fail("Expected internal error"); return null; })
                .orElse(f -> {
                    assertEquals("INTERNAL_ERROR", f.errorCode());
                    assertEquals("TOTP setup could not be completed.", f.errorMessage());
                    return null;
                });
        } finally {
            logger.setLevel(old);
        }
    }

    // ---------------------------------------------------------------- constructor guards

    @Test
    void constructorRejectsNullProvider() {
        assertThrows(NullPointerException.class,
            () -> new SetupTotpCommandHandler(null, ISSUER));
    }

    @Test
    void constructorRejectsNullIssuerName() {
        assertThrows(NullPointerException.class,
            () -> new SetupTotpCommandHandler(totpSetupProvider, null));
    }

    @Test
    void constructorRejectsBlankIssuerName() {
        assertThrows(IllegalArgumentException.class,
            () -> new SetupTotpCommandHandler(totpSetupProvider, ""));
        assertThrows(IllegalArgumentException.class,
            () -> new SetupTotpCommandHandler(totpSetupProvider, "   "));
    }
}

