package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.TotpCodeGenerator;
import com.oodesigns.cas.domain.value.BackupCode;
import com.oodesigns.cas.domain.value.KeyPassword;
import com.oodesigns.cas.domain.value.SecretFor2FA;
import com.oodesigns.cas.domain.value.TotpCode;
import com.oodesigns.cas.domain.value.UserId;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("SqlResolve")
@ExtendWith(MockitoExtension.class)
class JooqTotpVerifierTest {

    private static final String TEST_KEY = "0123456789ABCDEF0123456789ABCDEF";
    private static final String ENCRYPTION_KEY_ID = "TOTP_ENCRYPTION_KEY";
    private static final String SECRET = "JBSWY3DPEHPK3PXP";
    private static final Instant FIXED_NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Mock
    private DSLContext dslContext;

    @Mock
    private KeySupplier keySupplier;

    private PasswordEncoder passwordEncoder;
    private TotpCodeGenerator totpCodeGenerator;
    private JooqTotpVerifier verifier;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        totpCodeGenerator = new TotpCodeGenerator(() -> FIXED_NOW);
        verifier = new JooqTotpVerifier(dslContext, totpCodeGenerator, passwordEncoder, keySupplier, ENCRYPTION_KEY_ID);
    }

    @Test
    void constructorRejectsNullDsl() {
        assertThrows(NullPointerException.class,
            () -> new JooqTotpVerifier(null, totpCodeGenerator, passwordEncoder, keySupplier, ENCRYPTION_KEY_ID));
    }

    @Test
    void publicConstructorInitialisesAdapter() {
        new JooqTotpVerifier(dslContext, () -> FIXED_NOW, keySupplier, ENCRYPTION_KEY_ID);
    }

    @Test
    void verifyCodeReturnsTrueForMatchingSecret() {
        final UUID userId = UUID.randomUUID();
        final String code = totpCodeGenerator.generate(SecretFor2FA.of(SECRET));
        when(keySupplier.getPassword(ENCRYPTION_KEY_ID)).thenReturn(Optional.of(KeyPassword.of(TEST_KEY)));
        when(dslContext.fetchOptional("SELECT * FROM api_schema.get_totp_secret(?)", userId))
            .thenReturn(Optional.of(secretRecord()));

        assertTrue(verifier.verifyCode(UserId.of(userId), TotpCode.of(code)));
        verify(dslContext).fetchOptional("SELECT * FROM api_schema.get_totp_secret(?)", userId);
    }

    @Test
    void verifyCodeReturnsFalseWhenSecretCannotBeLoaded() {
        final UUID userId = UUID.randomUUID();
        when(dslContext.fetchOptional("SELECT * FROM api_schema.get_totp_secret(?)", userId))
            .thenReturn(Optional.empty());

        assertFalse(verifier.verifyCode(UserId.of(userId), TotpCode.of("123456")));
        verifyNoInteractions(keySupplier);
    }

    @Test
    void verifyCodeReturnsFalseWhenInputsAreNull() {
        assertFalse(verifier.verifyCode(null, TotpCode.of("123456")));
        assertFalse(verifier.verifyCode(UserId.of(UUID.randomUUID()), null));
    }

    @Test
    void verifyBackupCodeConsumesMatchingHashAndMarksLastUsed() {
        final UUID userId = UUID.randomUUID();
        final BackupCode backupCode = BackupCode.of("ABCD-EFGH-IJKL-MNPQ");
        final String hash = passwordEncoder.encode(backupCode.getCode());
        when(dslContext.fetchOne("SELECT api_schema.find_unused_backup_code_hashes(?)", userId))
            .thenReturn(backupHashesRecord(hash));
        when(dslContext.fetchOne("SELECT api_schema.consume_backup_code(?, ?)", userId, hash))
            .thenReturn(trueRecord());
        when(dslContext.fetchOne("SELECT api_schema.mark_totp_last_used(?)", userId))
            .thenReturn(trueRecord());

        assertTrue(verifier.verifyBackupCode(UserId.of(userId), backupCode));
        verify(dslContext).fetchOne("SELECT api_schema.consume_backup_code(?, ?)", userId, hash);
        verify(dslContext).fetchOne("SELECT api_schema.mark_totp_last_used(?)", userId);
    }

    @Test
    void verifyBackupCodeReturnsFalseWhenNoUnusedHashesExist() {
        final UUID userId = UUID.randomUUID();
        when(dslContext.fetchOne("SELECT api_schema.find_unused_backup_code_hashes(?)", userId))
            .thenReturn(null);

        assertFalse(verifier.verifyBackupCode(UserId.of(userId), BackupCode.of("ABCD-EFGH-IJKL-MNPQ")));
        verify(dslContext, never()).fetchOne("SELECT api_schema.consume_backup_code(?, ?)", userId, "unused");
    }

    @Test
    void verifyBackupCodeReturnsFalseWhenInputsAreNull() {
        assertFalse(verifier.verifyBackupCode(null, BackupCode.of("ABCD-EFGH-IJKL-MNPQ")));
        assertFalse(verifier.verifyBackupCode(UserId.of(UUID.randomUUID()), null));
    }

    @Test
    void isTotpEnabledReturnsTrueWhenStatusRowExists() {
        final UUID userId = UUID.randomUUID();
        when(dslContext.fetchOptional("SELECT * FROM api_schema.get_totp_status(?)", userId))
            .thenReturn(Optional.of(statusRecord(userId)));

        assertTrue(verifier.isTotpEnabled(UserId.of(userId)));
    }

    @Test
    void isTotpEnabledReturnsFalseWhenStatusRowMissing() {
        final UUID userId = UUID.randomUUID();
        when(dslContext.fetchOptional("SELECT * FROM api_schema.get_totp_status(?)", userId))
            .thenReturn(Optional.empty());

        assertFalse(verifier.isTotpEnabled(UserId.of(userId)));
    }

    @Test
    void isTotpEnabledReturnsFalseWhenUserIdIsNull() {
        assertFalse(verifier.isTotpEnabled(null));
    }

    private Record secretRecord() {
        final byte[] ciphertext = TotpSecretCipher.encrypt(
            SECRET,
            KeyPassword.of(TEST_KEY),
            new SecureRandom()
        );
        return mock(Record.class, invocation -> {
            final Object[] args = invocation.getArguments();
            if (args.length == 2 && "secret_key_encrypted".equals(args[0])) {
                return ciphertext;
            }
            if (args.length == 2 && "algorithm".equals(args[0])) {
                return "SHA1";
            }
            if (args.length == 2 && "period_seconds".equals(args[0])) {
                return 30;
            }
            if (args.length == 2 && "digits".equals(args[0])) {
                return 6;
            }
            return null;
        });
    }

    private Record backupHashesRecord(final String hash) {
        return mock(Record.class, invocation -> {
            final Object[] args = invocation.getArguments();
            if (args.length == 2 && args[0] instanceof Integer && ((Integer) args[0]) == 0) {
                return new String[]{hash};
            }
            return null;
        });
    }

    private Record trueRecord() {
        return mock(Record.class, invocation -> {
            final Object[] args = invocation.getArguments();
            if (args.length == 2 && args[0] instanceof Integer && ((Integer) args[0]) == 0) {
                return Boolean.TRUE;
            }
            return null;
        });
    }

    private Record statusRecord(final UUID userId) {
        return mock(Record.class, invocation -> {
            final Object[] args = invocation.getArguments();
            if (args.length == 2 && "user_id".equals(args[0])) {
                return userId;
            }
            return null;
        });
    }
}

