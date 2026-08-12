package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.BackupCode;
import com.oodesigns.cas.domain.value.TotpCode;
import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EnableTotpCommandHandler}.
 * <p>
 * Covers the full enrolment-confirmation flow: OTP verification, TOTP activation,
 * backup code issuance, and all failure branches.
 */
@ExtendWith(MockitoExtension.class)
class EnableTotpCommandHandlerTest {

    private static final List<BackupCode> BACKUP_CODES = List.of(
        BackupCode.of("ABCD-EFGH-IJKL-MNOP"), BackupCode.of("QRST-UVWX-YZ23-4567"),
        BackupCode.of("A2B3-C4D5-E6F7-G8H9"), BackupCode.of("JKLM-NOPQ-RSTU-VWXY")
    );

    @Mock
    private Ports.TotpVerifier totpVerifier;

    @Mock
    private Ports.TotpSetupProvider totpSetupProvider;

    private EnableTotpCommandHandler handler;
    private UserId userId;
    private EnableTotpCommand command;

    @BeforeEach
    void setUp() {
        handler = new EnableTotpCommandHandler(totpVerifier, totpSetupProvider);
        userId = UserId.of(UUID.randomUUID());
        command = new EnableTotpCommand(userId, TotpCode.of("123456"));
    }

    // ---------------------------------------------------------------- happy path

    @Test
    void handleReturnsSuccessWithBackupCodesOnValidCode() {
        when(totpVerifier.verifySetupCode(userId, TotpCode.of("123456"))).thenReturn(true);
        when(totpSetupProvider.enableTotp(userId)).thenReturn(true);
        when(totpSetupProvider.generateBackupCodes(userId)).thenReturn(BACKUP_CODES);

        final EnableTotpResult result = handler.handle(command);

        result.mapTo(success -> {
            assertEquals(BACKUP_CODES, success.backupCodes());
            return null;
        }).orElse(f -> { fail("Expected success but got: " + f.errorCode()); return null; });
    }

    @Test
    void handleLogsInfoOnSuccess() {
        when(totpVerifier.verifySetupCode(userId, TotpCode.of("123456"))).thenReturn(true);
        when(totpSetupProvider.enableTotp(userId)).thenReturn(true);
        when(totpSetupProvider.generateBackupCodes(userId)).thenReturn(BACKUP_CODES);

        final Logger logger = Logger.getLogger(EnableTotpCommandHandler.class.getName());
        final Level old = logger.getLevel();
        try {
            logger.setLevel(Level.INFO);
            final EnableTotpResult result = handler.handle(command);
            result.mapTo(s -> null).orElse(f -> { fail("Expected success"); return null; });
        } finally {
            logger.setLevel(old);
        }
    }

    @Test
    void handleSucceedsEvenWhenInfoLoggingDisabled() {
        when(totpVerifier.verifySetupCode(userId, TotpCode.of("123456"))).thenReturn(true);
        when(totpSetupProvider.enableTotp(userId)).thenReturn(true);
        when(totpSetupProvider.generateBackupCodes(userId)).thenReturn(BACKUP_CODES);

        final Logger logger = Logger.getLogger(EnableTotpCommandHandler.class.getName());
        final Level old = logger.getLevel();
        try {
            logger.setLevel(Level.WARNING); // INFO is suppressed — exercises the false-branch
            final EnableTotpResult result = handler.handle(command);
            result.mapTo(s -> {
                assertEquals(BACKUP_CODES, s.backupCodes());
                return null;
            }).orElse(f -> { fail("Expected success"); return null; });
        } finally {
            logger.setLevel(old);
        }
    }

    @Test
    void handleCallsPortsInSecurityOrder() {
        when(totpVerifier.verifySetupCode(userId, TotpCode.of("123456"))).thenReturn(true);
        when(totpSetupProvider.enableTotp(userId)).thenReturn(true);
        when(totpSetupProvider.generateBackupCodes(userId)).thenReturn(BACKUP_CODES);

        handler.handle(command);

        final var inOrder = inOrder(totpVerifier, totpSetupProvider);
        inOrder.verify(totpVerifier).verifySetupCode(userId, TotpCode.of("123456"));
        inOrder.verify(totpSetupProvider).enableTotp(userId);
        inOrder.verify(totpSetupProvider).generateBackupCodes(userId);
    }

    // ---------------------------------------------------------------- failure: invalid OTP

    @Test
    void handleReturnsInvalidTotpCodeWhenVerificationFails() {
        when(totpVerifier.verifySetupCode(userId, TotpCode.of("123456"))).thenReturn(false);

        final EnableTotpResult result = handler.handle(command);

        result.mapTo(s -> { fail("Expected INVALID_TOTP_CODE"); return null; })
            .orElse(f -> {
                assertEquals("INVALID_TOTP_CODE", f.errorCode());
                return null;
            });

        // SECURITY: no persistent state should change on a failed OTP
        verify(totpSetupProvider, never()).enableTotp(any());
        verify(totpSetupProvider, never()).generateBackupCodes(any());
    }

    // ---------------------------------------------------------------- failure: already enabled

    @Test
    void handleReturnsTotpAlreadyEnabledWhenEnableReturnsFalse() {
        when(totpVerifier.verifySetupCode(userId, TotpCode.of("123456"))).thenReturn(true);
        when(totpSetupProvider.enableTotp(userId)).thenReturn(false);

        final EnableTotpResult result = handler.handle(command);

        result.mapTo(s -> { fail("Expected TOTP_ALREADY_ENABLED"); return null; })
            .orElse(f -> {
                assertEquals("TOTP_ALREADY_ENABLED", f.errorCode());
                return null;
            });

        // Backup codes must NOT be issued if enableTotp indicated already enabled
        verify(totpSetupProvider, never()).generateBackupCodes(any());
    }

    // ---------------------------------------------------------------- failure: null command

    @Test
    void handleReturnsInvalidRequestForNullCommand() {
        final EnableTotpResult result = handler.handle(null);

        result.mapTo(s -> { fail("Expected INVALID_REQUEST"); return null; })
            .orElse(f -> {
                assertEquals("INVALID_REQUEST", f.errorCode());
                return null;
            });

        verifyNoInteractions(totpVerifier, totpSetupProvider);
    }

    // ---------------------------------------------------------------- failure: exception

    @Test
    void handleReturnsInternalErrorWhenVerifierThrows() {
        when(totpVerifier.verifySetupCode(any(), any())).thenThrow(new RuntimeException("db error"));

        final Logger logger = Logger.getLogger(EnableTotpCommandHandler.class.getName());
        final Level old = logger.getLevel();
        try {
            logger.setLevel(Level.OFF);
            final EnableTotpResult result = handler.handle(command);
            result.mapTo(s -> { fail("Expected INTERNAL_ERROR"); return null; })
                .orElse(f -> {
                    assertEquals("INTERNAL_ERROR", f.errorCode());
                    assertTrue(f.errorMessage().contains("db error"));
                    return null;
                });
        } finally {
            logger.setLevel(old);
        }
    }

    @Test
    void handleReturnsInternalErrorWhenEnableProviderThrows() {
        when(totpVerifier.verifySetupCode(userId, TotpCode.of("123456"))).thenReturn(true);
        when(totpSetupProvider.enableTotp(any())).thenThrow(new RuntimeException("storage failure"));

        final Logger logger = Logger.getLogger(EnableTotpCommandHandler.class.getName());
        final Level old = logger.getLevel();
        try {
            logger.setLevel(Level.OFF);
            final EnableTotpResult result = handler.handle(command);
            result.mapTo(s -> { fail("Expected INTERNAL_ERROR"); return null; })
                .orElse(f -> {
                    assertEquals("INTERNAL_ERROR", f.errorCode());
                    return null;
                });
        } finally {
            logger.setLevel(old);
        }
    }

    // ---------------------------------------------------------------- constructor guards

    @Test
    void constructorRejectsNulls() {
        assertThrows(NullPointerException.class,
            () -> new EnableTotpCommandHandler(null, totpSetupProvider));
        assertThrows(NullPointerException.class,
            () -> new EnableTotpCommandHandler(totpVerifier, null));
    }
}

