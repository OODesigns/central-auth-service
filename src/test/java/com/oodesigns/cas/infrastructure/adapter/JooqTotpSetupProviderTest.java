package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.application.command.DisableReason;
import com.oodesigns.cas.domain.service.BackupCodeGenerator;
import com.oodesigns.cas.domain.value.BackupCode;
import com.oodesigns.cas.domain.value.KeyPassword;
import com.oodesigns.cas.domain.value.UserId;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JooqTotpSetupProviderTest {

    private static final String TEST_KEY = "0123456789ABCDEF0123456789ABCDEF";
    private static final String ENCRYPTION_KEY_ID = "TOTP_ENCRYPTION_KEY";

    @Mock
    private DSLContext dslContext;

    @Mock
    private KeySupplier keySupplier;

    @Mock
    private BackupCodeGenerator backupCodeGenerator;

    @Mock
    private PasswordEncoder passwordEncoder;

    private JooqTotpSetupProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JooqTotpSetupProvider(
            dslContext,
            keySupplier,
            ENCRYPTION_KEY_ID,
            backupCodeGenerator,
            new DeterministicSecureRandom(),
            passwordEncoder
        );
    }

    @Test
    void constructorRejectsNullDsl() {
        assertThrows(NullPointerException.class,
            () -> new JooqTotpSetupProvider(null, keySupplier, ENCRYPTION_KEY_ID, backupCodeGenerator,
                new DeterministicSecureRandom(), passwordEncoder));
    }

    @Test
    void publicConstructorInitialisesAdapter() {
        new JooqTotpSetupProvider(dslContext, keySupplier, ENCRYPTION_KEY_ID);
    }

    @Test
    void generateSecretPersistsEncryptedSecretAndReturnsBase32Secret() {
        when(keySupplier.getPassword(ENCRYPTION_KEY_ID)).thenAnswer(invocation -> Optional.of(KeyPassword.of(TEST_KEY)));

        final String secret = provider.generateSecret(UserId.of(UUID.randomUUID()));

        assertEquals("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", secret);
        final ArgumentCaptor<byte[]> ciphertextCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(dslContext).execute(eq("SELECT api_schema.store_totp_secret(?, ?)"), any(UUID.class), ciphertextCaptor.capture());
        assertTrue(ciphertextCaptor.getValue().length > 16);
        assertNotNull(ciphertextCaptor.getValue());
        verify(keySupplier).getPassword(ENCRYPTION_KEY_ID);
    }

    @Test
    void generateSecretThrowsWhenEncryptionKeyIsUnavailable() {
        when(keySupplier.getPassword(ENCRYPTION_KEY_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> provider.generateSecret(UserId.of(UUID.randomUUID())));
    }

    @Test
    void enableTotpReturnsTrueWhenDatabaseUpdatesRow() {
        final UUID userId = UUID.randomUUID();
        final Record record = mock(Record.class);
        when(record.get(0, Boolean.class)).thenReturn(Boolean.TRUE);
        when(dslContext.fetchOne("SELECT api_schema.enable_totp(?)", userId)).thenReturn(record);

        assertTrue(provider.enableTotp(UserId.of(userId)));
    }

    @Test
    void enableTotpReturnsFalseWhenDatabaseDoesNotUpdateRow() {
        final UUID userId = UUID.randomUUID();
        when(dslContext.fetchOne("SELECT api_schema.enable_totp(?)", userId)).thenReturn(null);

        assertFalse(provider.enableTotp(UserId.of(userId)));
    }

    @Test
    void disableTotpReturnsTrueWhenDatabaseDeletesRow() {
        final UUID userId = UUID.randomUUID();
        final Record record = mock(Record.class);
        when(record.get(0, Boolean.class)).thenReturn(Boolean.TRUE);
        when(dslContext.fetchOne("SELECT api_schema.disable_totp(?, ?)", userId, "USER_REQUESTED"))
            .thenReturn(record);

        assertTrue(provider.disableTotp(UserId.of(userId), DisableReason.USER_REQUESTED));
    }

    @Test
    void disableTotpReturnsFalseWhenDatabaseDeletesNoRow() {
        final UUID userId = UUID.randomUUID();
        when(dslContext.fetchOne("SELECT api_schema.disable_totp(?, ?)", userId, "USER_REQUESTED"))
            .thenReturn(null);

        assertFalse(provider.disableTotp(UserId.of(userId), DisableReason.USER_REQUESTED));
    }

    @Test
    void generateBackupCodesHashesAndPersistsBatch() {
        final UUID userId = UUID.randomUUID();
        final List<BackupCode> codes = List.of(
            BackupCode.of("ABCD-EFGH-IJKL-MNPQ"),
            BackupCode.of("RSTU-VWXY-Z234-5678")
        );
        when(backupCodeGenerator.generateBatch()).thenReturn(codes);
        when(passwordEncoder.encode(codes.get(0).getCode())).thenReturn("hash-1");
        when(passwordEncoder.encode(codes.get(1).getCode())).thenReturn("hash-2");

        final List<BackupCode> result = provider.generateBackupCodes(UserId.of(userId));

        assertEquals(codes, result);
        verify(passwordEncoder).encode(codes.get(0).getCode());
        verify(passwordEncoder).encode(codes.get(1).getCode());

        final ArgumentCaptor<String[]> hashesCaptor = ArgumentCaptor.forClass(String[].class);
        final ArgumentCaptor<UUID> batchIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(dslContext).execute(eq("SELECT api_schema.insert_backup_codes(?, ?, ?)"), eq(userId), hashesCaptor.capture(), batchIdCaptor.capture());
        assertEquals("hash-1", hashesCaptor.getValue()[0]);
        assertEquals("hash-2", hashesCaptor.getValue()[1]);
        assertNotNull(batchIdCaptor.getValue());
    }

    @Test
    void generateBackupCodesRejectsNullUserId() {
        assertThrows(NullPointerException.class, () -> provider.generateBackupCodes(null));
    }

    private static final class DeterministicSecureRandom extends SecureRandom {
        @Override
        public int nextInt(final int bound) {
            return 0;
        }

        @Override
        public void nextBytes(final byte[] bytes) {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}


