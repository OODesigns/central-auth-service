package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.BackupCodeGenerator;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.BackupCode;
import com.oodesigns.cas.domain.value.KeyPassword;
import com.oodesigns.cas.domain.value.UserId;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * JOOQ-based implementation of {@link Ports.TotpSetupProvider}.
 * <p>
 * Generates a new Base32 TOTP secret, encrypts it with on-demand key material, stores it in
 * {@code totp_secrets}, and manages the enable/disable and backup-code lifecycle through
 * SECURITY DEFINER API functions.
 */
public final class JooqTotpSetupProvider implements Ports.TotpSetupProvider {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_LENGTH = 32;

    private final DSLContext dsl;
    private final KeySupplier keySupplier;
    private final String encryptionKeyId;
    private final BackupCodeGenerator backupCodeGenerator;
    private final SecureRandom secureRandom;
    private final PasswordEncoder passwordEncoder;
    private final AuditTransaction auditTransaction;

    public JooqTotpSetupProvider(final DSLContext dsl,
                                 final KeySupplier keySupplier,
                                 final String encryptionKeyId) {
        this(dsl, keySupplier, encryptionKeyId, new BackupCodeGenerator(), new SecureRandom(),
            new BCryptPasswordEncoder(), new JooqAuditTransaction(dsl));
    }

    JooqTotpSetupProvider(final DSLContext dsl,
                          final KeySupplier keySupplier,
                          final String encryptionKeyId,
                          final BackupCodeGenerator backupCodeGenerator,
                          final SecureRandom secureRandom,
                          final PasswordEncoder passwordEncoder,
                          final AuditTransaction auditTransaction) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext cannot be null");
        this.keySupplier = Objects.requireNonNull(keySupplier, "KeySupplier cannot be null");
        this.encryptionKeyId = Objects.requireNonNull(encryptionKeyId, "Encryption key ID cannot be null");
        this.backupCodeGenerator = Objects.requireNonNull(backupCodeGenerator, "BackupCodeGenerator cannot be null");
        this.secureRandom = Objects.requireNonNull(secureRandom, "SecureRandom cannot be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoder cannot be null");
        this.auditTransaction = Objects.requireNonNull(auditTransaction, "Audit transaction cannot be null");
    }

    @Override
    public String generateSecret(final UserId userId) {
        final UserId id = Objects.requireNonNull(userId, "User ID cannot be null");
        final String secret = generateBase32Secret();
        persistSecret(id, secret);
        return secret;
    }

    @Override
    public boolean enableTotp(final UserId userId) {
        final UserId id = Objects.requireNonNull(userId, "User ID cannot be null");
        return auditTransaction.execute(transactionDsl ->
            fetchBoolean(transactionDsl, "SELECT api_schema.enable_totp(?)", id.value()));
    }

    @Override
    public boolean disableTotp(final UserId userId, final com.oodesigns.cas.application.command.DisableReason reason) {
        final UserId id = Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(reason, "Disable reason cannot be null");
        return auditTransaction.execute(transactionDsl ->
            fetchBoolean(transactionDsl, "SELECT api_schema.disable_totp(?, ?)", id.value(), reason.name()));
    }

    @Override
    public List<BackupCode> generateBackupCodes(final UserId userId) {
        final UserId id = Objects.requireNonNull(userId, "User ID cannot be null");
        final UUID batchId = UUID.randomUUID();
        final List<BackupCode> codes = backupCodeGenerator.generateBatch();
        final String[] hashes = codes.stream()
            .map(code -> code.getCode())
            .map(passwordEncoder::encode)
            .toArray(String[]::new);

        auditTransaction.execute(transactionDsl -> {
            transactionDsl.execute("SELECT api_schema.insert_backup_codes(?, ?, ?)", id.value(), hashes, batchId);
            return null;
        });
        return codes;
    }

    private void persistSecret(final UserId userId, final String secret) {
        final KeyPassword keyPassword = encryptionKey();
        try (keyPassword) {
            final byte[] encrypted = TotpSecretCipher.encrypt(secret, keyPassword, secureRandom);
            try {
                auditTransaction.execute(transactionDsl -> {
                    transactionDsl.execute("SELECT api_schema.store_totp_secret(?, ?)", userId.value(), encrypted);
                    return null;
                });
            } finally {
                java.util.Arrays.fill(encrypted, (byte) 0);
            }
        }
    }

    private boolean fetchBoolean(final DSLContext context, final String sql, final Object... parameters) {
        return Optional.ofNullable(context.fetchOne(sql, parameters))
            .map(record -> record.get(0, Boolean.class))
            .orElse(false);
    }

    private KeyPassword encryptionKey() {
        return keySupplier.getPassword(encryptionKeyId)
            .orElseThrow(() -> new IllegalStateException("TOTP encryption key is unavailable"));
    }

    private String generateBase32Secret() {
        final StringBuilder builder = new StringBuilder(SECRET_LENGTH);
        for (int i = 0; i < SECRET_LENGTH; i++) {
            builder.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
