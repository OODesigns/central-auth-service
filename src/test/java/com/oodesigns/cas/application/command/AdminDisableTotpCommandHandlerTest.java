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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDisableTotpCommandHandlerTest {

    private static final String VALID_PASSWORD = "ValidPassword1234";
    private static final String BCRYPT_HASH = "$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";

    @Mock
    private Ports.UserCredentialByIdRetriever credentialReader;

    @Mock
    private Ports.TotpSetupProvider totpSetupProvider;

    @Mock
    private Ports.PasswordVerifier passwordVerifier;

    private AuthenticationService authenticationService;
    private AdminDisableTotpCommandHandler handler;

    private UserId adminId;
    private UserId targetId;
    private UserCredential adminCredential;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(passwordVerifier);
        handler = new AdminDisableTotpCommandHandler(authenticationService, credentialReader, totpSetupProvider);
        adminId = UserId.of(UUID.randomUUID());
        targetId = UserId.of(UUID.randomUUID());
        adminCredential = UserCredential.of(adminId, PasswordHash.of(BCRYPT_HASH));
    }

    @Test
    void constructorRejectsNullDependencies() {
        assertThrows(NullPointerException.class,
            () -> new AdminDisableTotpCommandHandler(null, credentialReader, totpSetupProvider));
        assertThrows(NullPointerException.class,
            () -> new AdminDisableTotpCommandHandler(authenticationService, null, totpSetupProvider));
        assertThrows(NullPointerException.class,
            () -> new AdminDisableTotpCommandHandler(authenticationService, credentialReader, null));
    }

    @Test
    void handleSucceedsForAuthorizedCrossUserDisable() {
        when(credentialReader.findCredentialsByUserId(adminId)).thenReturn(Optional.of(adminCredential));
        when(passwordVerifier.verify(any())).thenReturn(Optional.of(adminId));
        when(totpSetupProvider.disableTotp(targetId, DisableReason.ADMIN_FORCED)).thenReturn(true);

        final DisableTotpResult result = handler.handle(command(DisableReason.ADMIN_FORCED));

        result.mapTo(success -> {
            assertNotNull(success);
            return null;
        }).orElse(failure -> fail("Expected success"));
        verify(totpSetupProvider).disableTotp(targetId, DisableReason.ADMIN_FORCED);
    }

    @Test
    void handleFailsWhenAdminCredentialMissing() {
        when(credentialReader.findCredentialsByUserId(adminId)).thenReturn(Optional.empty());

        final DisableTotpResult result = handler.handle(command(DisableReason.ADMIN_FORCED));

        result.mapTo(success -> fail("Expected failure")).orElse(failure -> {
            assertEquals("INVALID_PASSWORD", failure.errorCode());
            return null;
        });
        verify(totpSetupProvider, never()).disableTotp(any(), any());
    }

    @Test
    void handleFailsWhenAdminPasswordInvalid() {
        when(credentialReader.findCredentialsByUserId(adminId)).thenReturn(Optional.of(adminCredential));
        when(passwordVerifier.verify(any())).thenReturn(Optional.empty());

        final DisableTotpResult result = handler.handle(command(DisableReason.SECURITY_INCIDENT));

        result.mapTo(success -> fail("Expected failure")).orElse(failure -> {
            assertEquals("INVALID_PASSWORD", failure.errorCode());
            return null;
        });
        verify(totpSetupProvider, never()).disableTotp(any(), any());
    }

    @Test
    void handleFailsWhenTargetHasNoTotp() {
        when(credentialReader.findCredentialsByUserId(adminId)).thenReturn(Optional.of(adminCredential));
        when(passwordVerifier.verify(any())).thenReturn(Optional.of(adminId));
        when(totpSetupProvider.disableTotp(targetId, DisableReason.RECOVERY_FLOW)).thenReturn(false);

        final DisableTotpResult result = handler.handle(command(DisableReason.RECOVERY_FLOW));

        result.mapTo(success -> fail("Expected failure")).orElse(failure -> {
            assertEquals("TOTP_NOT_ENABLED", failure.errorCode());
            return null;
        });
    }

    private AdminDisableTotpCommand command(final DisableReason reason) {
        return new AdminDisableTotpCommand(adminId, Password.of(VALID_PASSWORD), targetId, reason);
    }
}
