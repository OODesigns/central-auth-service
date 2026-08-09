package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.PasswordHash;
import com.oodesigns.cas.domain.value.UserCredential;
import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DisableTotpCommandHandler.
 * <p>
 * Focus: the re-authentication gate. Disabling 2FA is a security-downgrading operation,
 * so every path that could bypass password verification is covered explicitly.
 */
@ExtendWith(MockitoExtension.class)
class DisableTotpCommandHandlerTest {

    private static final String VALID_PASSWORD = "ValidPassword1234";
    private static final String BCRYPT_HASH = "$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";

    @Mock
    private Ports.UserCredentialByIdRetriever credentialReader;

    @Mock
    private Ports.TotpSetupProvider totpSetupProvider;

    @Mock
    private Ports.PasswordVerifier passwordVerifier;

    private AuthenticationService authService;
    private DisableTotpCommandHandler handler;
    private UserId userId;
    private UserCredential credential;

    @BeforeEach
    void setUp() {
        authService = new AuthenticationService(passwordVerifier);
        handler = new DisableTotpCommandHandler(authService, credentialReader, totpSetupProvider);
        userId = UserId.of(UUID.randomUUID());
        credential = UserCredential.of(userId, PasswordHash.of(BCRYPT_HASH));
    }

    private DisableTotpCommand command() {
        return new DisableTotpCommand(userId, Password.of(VALID_PASSWORD), DisableReason.USER_REQUESTED);
    }

    @Test
    void constructorRejectsNulls() {
        assertThrows(NullPointerException.class,
            () -> new DisableTotpCommandHandler(null, credentialReader, totpSetupProvider));
        assertThrows(NullPointerException.class,
            () -> new DisableTotpCommandHandler(authService, null, totpSetupProvider));
        assertThrows(NullPointerException.class,
            () -> new DisableTotpCommandHandler(authService, credentialReader, null));
    }

    @Test
    void handleReturnsSuccessWhenPasswordVerifiedAndTotpDisabled() {
        when(credentialReader.findCredentialsByUserId(userId)).thenReturn(Optional.of(credential));
        when(passwordVerifier.verify(any())).thenReturn(Optional.of(userId));
        when(totpSetupProvider.disableTotp(eq(userId), any())).thenReturn(true);

        // ensure INFO logging branch is exercised
        final Logger logger = Logger.getLogger(DisableTotpCommandHandler.class.getName());
        final Level old = logger.getLevel();
        try {
            logger.setLevel(Level.INFO);
            final DisableTotpResult res = handler.handle(command());
            res.mapTo(s -> {
                assertNotNull(s);
                return null;
            }).orElse(f -> fail("Expected success"));
        } finally {
            logger.setLevel(old);
        }
    }

    @Test
    void handleReturnsFailureWhenTotpNotEnabled() {
        when(credentialReader.findCredentialsByUserId(userId)).thenReturn(Optional.of(credential));
        when(passwordVerifier.verify(any())).thenReturn(Optional.of(userId));
        when(totpSetupProvider.disableTotp(eq(userId), any())).thenReturn(false);

        final DisableTotpResult res = handler.handle(command());
        res.mapTo(s -> {
            fail("Expected failure");
            return null;
        }).orElse(f -> {
            assertEquals("TOTP_NOT_ENABLED", f.errorCode());
            return null;
        });
    }

    @Test
    void handleRejectsWhenCredentialNotFound() {
        // User ID does not resolve to a stored credential → cannot re-authenticate
        when(credentialReader.findCredentialsByUserId(userId)).thenReturn(Optional.empty());

        final DisableTotpResult res = handler.handle(command());
        res.mapTo(s -> {
            fail("Expected INVALID_PASSWORD when credential is missing");
            return null;
        }).orElse(f -> {
            assertEquals("INVALID_PASSWORD", f.errorCode());
            return null;
        });

        // Critically: 2FA must NOT be disabled without successful re-authentication
        verify(totpSetupProvider, never()).disableTotp(any(), any());
    }

    @Test
    void handleRejectsWhenPasswordIsWrong() {
        when(credentialReader.findCredentialsByUserId(userId)).thenReturn(Optional.of(credential));
        // Password verifier rejects the password
        when(passwordVerifier.verify(any())).thenReturn(Optional.empty());

        final DisableTotpResult res = handler.handle(command());
        res.mapTo(s -> {
            fail("Expected INVALID_PASSWORD for wrong password");
            return null;
        }).orElse(f -> {
            assertEquals("INVALID_PASSWORD", f.errorCode());
            return null;
        });

        verify(totpSetupProvider, never()).disableTotp(any(), any());
    }

    @Test
    void handleRejectsWhenVerifiedUserIdDoesNotMatchCommandUserId() {
        // Defence in depth: even if the verifier authenticates *someone*, it must be
        // the same user whose 2FA is being disabled.
        final UserId otherUserId = UserId.of(UUID.randomUUID());
        when(credentialReader.findCredentialsByUserId(userId)).thenReturn(Optional.of(credential));
        when(passwordVerifier.verify(any())).thenReturn(Optional.of(otherUserId));

        final DisableTotpResult res = handler.handle(command());
        res.mapTo(s -> {
            fail("Expected INVALID_PASSWORD on user ID mismatch");
            return null;
        }).orElse(f -> {
            assertEquals("INVALID_PASSWORD", f.errorCode());
            return null;
        });

        verify(totpSetupProvider, never()).disableTotp(any(), any());
    }

    @Test
    void handleReturnsInternalErrorOnException() {
        when(credentialReader.findCredentialsByUserId(userId)).thenReturn(Optional.of(credential));
        when(passwordVerifier.verify(any())).thenReturn(Optional.of(userId));
        when(totpSetupProvider.disableTotp(eq(userId), any())).thenThrow(new RuntimeException("boom"));

        final Logger logger = Logger.getLogger(DisableTotpCommandHandler.class.getName());
        final Level old = logger.getLevel();
        try {
            logger.setLevel(Level.OFF);
            final DisableTotpResult res = handler.handle(command());
            res.mapTo(s -> {
                fail("Expected failure");
                return null;
            }).orElse(f -> {
                assertEquals("INTERNAL_ERROR", f.errorCode());
                return null;
            });
        } finally {
            logger.setLevel(old);
        }
    }
}

