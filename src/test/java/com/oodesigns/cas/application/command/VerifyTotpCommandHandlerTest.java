package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.value.*;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VerifyTotpCommandHandler}.
 * <p>
 * Covers: OTP and backup-code happy paths, all failure branches,
 * port-call ordering, null command, and exception wrapping.
 */
@ExtendWith(MockitoExtension.class)
class VerifyTotpCommandHandlerTest {

    private static final String VERIFICATION_TOKEN = "a.valid.jwt";
    private static final String VALID_OTP = "123456";
    private static final String VALID_BACKUP = "ABCD-EFGH-IJKL-MNOP";

    @Mock private Ports.TokenVerifier tokenVerifier;
    @Mock private Ports.TotpVerifier totpVerifier;
    @Mock private Ports.UserRetriever userRetriever;
    @Mock private Ports.TokenSigner tokenSigner;
    @Mock private Ports.Clock clock;

    private VerifyTotpCommandHandler handler;
    private TokenService tokenService;
    private UserId userId;
    private User user;
    private TokenService.TokenPair tokenPair;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(clock, tokenSigner);
        handler = new VerifyTotpCommandHandler(tokenVerifier, totpVerifier, userRetriever, tokenService);
        userId = UserId.of(UUID.randomUUID());
        user = new User(userId, Username.of("alice"), Set.of(Permission.of("read")), null, null);
        tokenPair = new TokenService.TokenPair("access.token", "refresh.token");
    }

    private void mockSuccessfulTokenGeneration() {
        when(clock.now()).thenReturn(Instant.now());
        when(tokenSigner.sign(any(), any())).thenReturn(Optional.of("signed.token"));
    }

    // ---------------------------------------------------------------- OTP happy path

    @Test
    void handleReturnsSuccessForValidOtpCode() {
        when(tokenVerifier.verify2FAVerificationToken(VERIFICATION_TOKEN))
            .thenReturn(Optional.of(userId));
        when(totpVerifier.verifyCode(userId, VALID_OTP)).thenReturn(true);
        when(userRetriever.findById(userId)).thenReturn(Optional.of(user));
        mockSuccessfulTokenGeneration();

        final VerifyTotpResult result =
            handler.handle(new VerifyTotpCommand(VERIFICATION_TOKEN, VALID_OTP));

        result.mapTo(success -> {
            assertNotNull(success.tokenPair());
            assertEquals(userId, success.userId());
            assertFalse(success.permissions().isEmpty());
            return null;
        }).orElse(f -> { fail("Expected success: " + f.errorCode()); return null; });
    }

    // ---------------------------------------------------------------- backup code happy path

    @Test
    void handleReturnsSuccessForValidBackupCode() {
        when(tokenVerifier.verify2FAVerificationToken(VERIFICATION_TOKEN))
            .thenReturn(Optional.of(userId));
        when(totpVerifier.verifyBackupCode(userId, VALID_BACKUP)).thenReturn(true);
        when(userRetriever.findById(userId)).thenReturn(Optional.of(user));
        mockSuccessfulTokenGeneration();

        final VerifyTotpResult result =
            handler.handle(new VerifyTotpCommand(VERIFICATION_TOKEN, VALID_BACKUP));

        result.mapTo(s -> { assertNotNull(s.tokenPair()); return null; })
            .orElse(f -> { fail("Expected success: " + f.errorCode()); return null; });
    }

    @Test
    void handleRoutesBackupCodeToVerifyBackupCode_notVerifyCode() {
        when(tokenVerifier.verify2FAVerificationToken(VERIFICATION_TOKEN))
            .thenReturn(Optional.of(userId));
        when(totpVerifier.verifyBackupCode(userId, VALID_BACKUP)).thenReturn(true);
        when(userRetriever.findById(userId)).thenReturn(Optional.of(user));
        mockSuccessfulTokenGeneration();

        handler.handle(new VerifyTotpCommand(VERIFICATION_TOKEN, VALID_BACKUP));

        verify(totpVerifier).verifyBackupCode(userId, VALID_BACKUP);
        verify(totpVerifier, never()).verifyCode(any(), any());
    }

    @Test
    void handleRoutesOtpToVerifyCode_notVerifyBackupCode() {
        when(tokenVerifier.verify2FAVerificationToken(VERIFICATION_TOKEN))
            .thenReturn(Optional.of(userId));
        when(totpVerifier.verifyCode(userId, VALID_OTP)).thenReturn(true);
        when(userRetriever.findById(userId)).thenReturn(Optional.of(user));
        mockSuccessfulTokenGeneration();

        handler.handle(new VerifyTotpCommand(VERIFICATION_TOKEN, VALID_OTP));

        verify(totpVerifier).verifyCode(userId, VALID_OTP);
        verify(totpVerifier, never()).verifyBackupCode(any(), any());
    }

    // ---------------------------------------------------------------- port call ordering

    @Test
    void handleCallsPortsInSecurityOrder() {
        when(tokenVerifier.verify2FAVerificationToken(VERIFICATION_TOKEN))
            .thenReturn(Optional.of(userId));
        when(totpVerifier.verifyCode(userId, VALID_OTP)).thenReturn(true);
        when(userRetriever.findById(userId)).thenReturn(Optional.of(user));
        mockSuccessfulTokenGeneration();

        handler.handle(new VerifyTotpCommand(VERIFICATION_TOKEN, VALID_OTP));

        final var inOrder = inOrder(tokenVerifier, totpVerifier, userRetriever);
        inOrder.verify(tokenVerifier).verify2FAVerificationToken(VERIFICATION_TOKEN);
        inOrder.verify(totpVerifier).verifyCode(userId, VALID_OTP);
        inOrder.verify(userRetriever).findById(userId);
    }

    // ---------------------------------------------------------------- INVALID_VERIFICATION_TOKEN

    @Test
    void handleReturnsInvalidVerificationTokenWhenVerifierRejectsToken() {
        when(tokenVerifier.verify2FAVerificationToken(VERIFICATION_TOKEN))
            .thenReturn(Optional.empty());

        final VerifyTotpResult result =
            handler.handle(new VerifyTotpCommand(VERIFICATION_TOKEN, VALID_OTP));

        result.mapTo(s -> { fail("Expected INVALID_VERIFICATION_TOKEN"); return null; })
            .orElse(f -> { assertEquals("INVALID_VERIFICATION_TOKEN", f.errorCode()); return null; });

        verifyNoInteractions(totpVerifier, userRetriever);
    }

    // ---------------------------------------------------------------- INVALID_TOTP_CODE

    @Test
    void handleReturnsInvalidTotpCodeWhenOtpFails() {
        when(tokenVerifier.verify2FAVerificationToken(VERIFICATION_TOKEN))
            .thenReturn(Optional.of(userId));
        when(totpVerifier.verifyCode(userId, VALID_OTP)).thenReturn(false);

        final VerifyTotpResult result =
            handler.handle(new VerifyTotpCommand(VERIFICATION_TOKEN, VALID_OTP));

        result.mapTo(s -> { fail("Expected INVALID_TOTP_CODE"); return null; })
            .orElse(f -> { assertEquals("INVALID_TOTP_CODE", f.errorCode()); return null; });

        verifyNoInteractions(userRetriever);
    }

    @Test
    void handleReturnsInvalidTotpCodeWhenBackupCodeFails() {
        when(tokenVerifier.verify2FAVerificationToken(VERIFICATION_TOKEN))
            .thenReturn(Optional.of(userId));
        when(totpVerifier.verifyBackupCode(userId, VALID_BACKUP)).thenReturn(false);

        final VerifyTotpResult result =
            handler.handle(new VerifyTotpCommand(VERIFICATION_TOKEN, VALID_BACKUP));

        result.mapTo(s -> { fail("Expected INVALID_TOTP_CODE"); return null; })
            .orElse(f -> { assertEquals("INVALID_TOTP_CODE", f.errorCode()); return null; });

        verifyNoInteractions(userRetriever);
    }

    // ---------------------------------------------------------------- USER_NOT_FOUND

    @Test
    void handleReturnsUserNotFoundWhenUserRepositoryReturnsEmpty() {
        when(tokenVerifier.verify2FAVerificationToken(VERIFICATION_TOKEN))
            .thenReturn(Optional.of(userId));
        when(totpVerifier.verifyCode(userId, VALID_OTP)).thenReturn(true);
        when(userRetriever.findById(userId)).thenReturn(Optional.empty());

        final VerifyTotpResult result =
            handler.handle(new VerifyTotpCommand(VERIFICATION_TOKEN, VALID_OTP));

        result.mapTo(s -> { fail("Expected USER_NOT_FOUND"); return null; })
            .orElse(f -> { assertEquals("USER_NOT_FOUND", f.errorCode()); return null; });
    }

    // ---------------------------------------------------------------- INTERNAL_ERROR (token signing fails)

    @Test
    void handleReturnsInternalErrorWhenTokenSigningFails() {
        when(tokenVerifier.verify2FAVerificationToken(VERIFICATION_TOKEN))
            .thenReturn(Optional.of(userId));
        when(totpVerifier.verifyCode(userId, VALID_OTP)).thenReturn(true);
        when(userRetriever.findById(userId)).thenReturn(Optional.of(user));
        when(clock.now()).thenReturn(Instant.now());
        when(tokenSigner.sign(any(), any())).thenReturn(Optional.empty());

        final VerifyTotpResult result =
            handler.handle(new VerifyTotpCommand(VERIFICATION_TOKEN, VALID_OTP));

        result.mapTo(s -> { fail("Expected INTERNAL_ERROR"); return null; })
            .orElse(f -> { assertEquals("INTERNAL_ERROR", f.errorCode()); return null; });
    }

    // ---------------------------------------------------------------- INTERNAL_ERROR (exception)

    @Test
    void handleReturnsInternalErrorWhenTokenVerifierThrows() {
        when(tokenVerifier.verify2FAVerificationToken(any()))
            .thenThrow(new RuntimeException("db down"));

        final Logger logger = Logger.getLogger(VerifyTotpCommandHandler.class.getName());
        final Level old = logger.getLevel();
        try {
            logger.setLevel(Level.OFF);
            final VerifyTotpResult result =
                handler.handle(new VerifyTotpCommand(VERIFICATION_TOKEN, VALID_OTP));
            result.mapTo(s -> { fail("Expected INTERNAL_ERROR"); return null; })
                .orElse(f -> { assertEquals("INTERNAL_ERROR", f.errorCode()); return null; });
        } finally {
            logger.setLevel(old);
        }
    }

    // ---------------------------------------------------------------- INVALID_REQUEST (null command)

    @Test
    void handleReturnsInvalidRequestForNullCommand() {
        final VerifyTotpResult result = handler.handle(null);

        result.mapTo(s -> { fail("Expected INVALID_REQUEST"); return null; })
            .orElse(f -> { assertEquals("INVALID_REQUEST", f.errorCode()); return null; });

        verifyNoInteractions(tokenVerifier, totpVerifier, userRetriever);
    }

    // ---------------------------------------------------------------- constructor guards

    @Test
    void constructorRejectsNulls() {
        assertThrows(NullPointerException.class,
            () -> new VerifyTotpCommandHandler(null, totpVerifier, userRetriever, tokenService));
        assertThrows(NullPointerException.class,
            () -> new VerifyTotpCommandHandler(tokenVerifier, null, userRetriever, tokenService));
        assertThrows(NullPointerException.class,
            () -> new VerifyTotpCommandHandler(tokenVerifier, totpVerifier, null, tokenService));
        assertThrows(NullPointerException.class,
            () -> new VerifyTotpCommandHandler(tokenVerifier, totpVerifier, userRetriever, null));
    }
}

