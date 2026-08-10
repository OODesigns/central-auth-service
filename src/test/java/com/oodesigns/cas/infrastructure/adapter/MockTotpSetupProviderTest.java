package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.application.command.DisableReason;
import com.oodesigns.cas.domain.value.BackupCode;
import com.oodesigns.cas.domain.value.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockTotpSetupProviderTest {

    @Test
    void setupProviderTracksSecretEnableDisableAndBackupCodes() {
        final MockTotpSetupProvider provider = new MockTotpSetupProvider();
        final UserId userId = UserId.of(UUID.randomUUID());

        final String secret = provider.generateSecret(userId);
        assertTrue(provider.wasSecretGenerated(userId));
        assertTrue(provider.secretForUser(userId).isPresent());
        assertEquals(secret, provider.secretForUser(userId).orElseThrow());
        assertFalse(provider.isEnabled(userId));

        assertTrue(provider.enableTotp(userId));
        assertTrue(provider.isEnabled(userId));
        assertFalse(provider.enableTotp(userId));

        final List<BackupCode> backupCodes = provider.generateBackupCodes(userId);
        assertEquals(3, backupCodes.size());
        assertEquals(backupCodes, provider.backupCodesFor(userId).orElseThrow());

        assertTrue(provider.disableTotp(userId, DisableReason.USER_REQUESTED));
        assertTrue(provider.isDisabled(userId));
        assertEquals(DisableReason.USER_REQUESTED, provider.disableReasonFor(userId).orElseThrow());
        assertFalse(provider.secretForUser(userId).isPresent());
    }

    @Test
    void disableTotpRejectsNullReason() {
        final MockTotpSetupProvider provider = new MockTotpSetupProvider();
        assertThrows(NullPointerException.class,
            () -> provider.disableTotp(UserId.of(UUID.randomUUID()), null));
    }

    @Test
    void clearRemovesState() {
        final MockTotpSetupProvider provider = new MockTotpSetupProvider();
        final UserId userId = UserId.of(UUID.randomUUID());
        provider.generateSecret(userId);
        provider.enableTotp(userId);
        provider.generateBackupCodes(userId);

        provider.clear();

        assertFalse(provider.wasSecretGenerated(userId));
        assertFalse(provider.isEnabled(userId));
        assertFalse(provider.backupCodesFor(userId).isPresent());
        assertFalse(provider.disableReasonFor(userId).isPresent());
    }
}

