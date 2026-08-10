package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.service.TotpCodeGenerator;
import com.oodesigns.cas.domain.value.BackupCode;
import com.oodesigns.cas.domain.value.KeyPassword;
import com.oodesigns.cas.domain.value.SecretFor2FA;
import com.oodesigns.cas.domain.value.TotpCode;
import com.oodesigns.cas.domain.value.UserId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * JOOQ-based implementation of {@link Ports.TotpVerifier}.
 * <p>
 * Retrieves encrypted TOTP secrets from the database, decrypts them with on-demand key
 * material, verifies time-based OTP codes, and consumes backup codes atomically.
 */
public final class JooqTotpVerifier implements Ports.TotpVerifier {

    private final DSLContext dsl;
    private final TotpCodeGenerator totpCodeGenerator;
    private final PasswordEncoder passwordEncoder;
    private final KeySupplier keySupplier;
    private final String encryptionKeyId;

    public JooqTotpVerifier(final DSLContext dsl,
                            final Ports.Clock clock,
                            final KeySupplier keySupplier,
                            final String encryptionKeyId) {
        this(dsl, new TotpCodeGenerator(clock), new BCryptPasswordEncoder(), keySupplier, encryptionKeyId);
    }

    JooqTotpVerifier(final DSLContext dsl,
                     final TotpCodeGenerator totpCodeGenerator,
                     final PasswordEncoder passwordEncoder,
                     final KeySupplier keySupplier,
                     final String encryptionKeyId) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext cannot be null");
        this.totpCodeGenerator = Objects.requireNonNull(totpCodeGenerator, "TotpCodeGenerator cannot be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoder cannot be null");
        this.keySupplier = Objects.requireNonNull(keySupplier, "KeySupplier cannot be null");
        this.encryptionKeyId = Objects.requireNonNull(encryptionKeyId, "Encryption key ID cannot be null");
    }

    @Override
    public boolean verifyCode(final UserId userId, final TotpCode totpCode) {
        return Optional.ofNullable(userId)
            .flatMap(id -> Optional.ofNullable(totpCode)
                .flatMap(code -> loadSecret(id).map(secret -> totpCodeGenerator.verify(secret, code.value()))))
            .orElse(false);
    }

    @Override
    public boolean verifyBackupCode(final UserId userId, final BackupCode backupCode) {
        return Optional.ofNullable(userId)
            .flatMap(id -> Optional.ofNullable(backupCode)
                .flatMap(code -> consumeMatchingBackupCode(id, code)))
            .orElse(false);
    }

    @Override
    public boolean isTotpEnabled(final UserId userId) {
        return Optional.ofNullable(userId)
            .flatMap(id -> Routines.getTotpStatus(dsl, id.value()).map(record -> true))
            .orElse(false);
    }

    private Optional<SecretFor2FA> loadSecret(final UserId userId) {
        return Routines.getTotpSecret(dsl, userId.value())
            .flatMap(record -> encryptionKey().map(key -> decryptSecret(record, key)))
            .filter(Objects::nonNull)
            .map(SecretFor2FA::of);
    }

    private Optional<Boolean> consumeMatchingBackupCode(final UserId userId, final BackupCode backupCode) {
        final String[] hashes = Routines.findUnusedBackupCodeHashes(dsl, userId.value());
        for (final String hash : hashes) {
            if (passwordEncoder.matches(backupCode.getCode(), hash) && Routines.consumeBackupCode(dsl, userId.value(), hash)) {
                Routines.markTotpLastUsed(dsl, userId.value());
                return Optional.of(true);
            }
        }
        return Optional.empty();
    }

    private Optional<KeyPassword> encryptionKey() {
        return keySupplier.getPassword(encryptionKeyId);
    }

    private String decryptSecret(final TotpSecretRecord record, final KeyPassword keyPassword) {
        try (keyPassword) {
            return TotpSecretCipher.decrypt(record.secretKeyEncrypted(), keyPassword);
        }
    }

    /**
     * Hand-written shim mirroring generated jOOQ routines for the required API calls.
     */
    private static final class Routines {
        static Optional<TotpSecretRecord> getTotpSecret(final DSLContext ctx, final UUID userId) {
            return ctx.fetchOptional("SELECT * FROM api_schema.get_totp_secret(?)", userId)
                .map(record -> new TotpSecretRecord(
                    record.get("secret_key_encrypted", byte[].class),
                    record.get("algorithm", String.class),
                    record.get("period_seconds", Integer.class),
                    record.get("digits", Integer.class)
                ));
        }

        static Optional<TotpStatusRecord> getTotpStatus(final DSLContext ctx, final UUID userId) {
            return ctx.fetchOptional("SELECT * FROM api_schema.get_totp_status(?)", userId)
                .map(record -> new TotpStatusRecord(record.get("user_id", UUID.class)));
        }

        static String[] findUnusedBackupCodeHashes(final DSLContext ctx, final UUID userId) {
            return Optional.ofNullable(ctx.fetchOne("SELECT api_schema.find_unused_backup_code_hashes(?)", userId))
                .map(record -> record.get(0, String[].class))
                .orElseGet(() -> new String[0]);
        }

        static boolean consumeBackupCode(final DSLContext ctx, final UUID userId, final String codeHash) {
            return Optional.ofNullable(ctx.fetchOne("SELECT api_schema.consume_backup_code(?, ?)", userId, codeHash))
                .map(record -> record.get(0, Boolean.class))
                .orElse(false);
        }

        static boolean markTotpLastUsed(final DSLContext ctx, final UUID userId) {
            return Optional.ofNullable(ctx.fetchOne("SELECT api_schema.mark_totp_last_used(?)", userId))
                .map(record -> record.get(0, Boolean.class))
                .orElse(false);
        }
    }

    private record TotpSecretRecord(byte[] secretKeyEncrypted, String algorithm, Integer periodSeconds, Integer digits) {
    }

    private record TotpStatusRecord(UUID userId) {
    }
}

