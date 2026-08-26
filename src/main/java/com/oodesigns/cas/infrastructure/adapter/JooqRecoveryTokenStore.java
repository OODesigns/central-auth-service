package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.PasswordHash;
import com.oodesigns.cas.domain.value.RecoveryToken;
import com.oodesigns.cas.domain.value.UserId;
import org.jooq.DSLContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/** Persists only SHA-256 hashes of recovery JWTs through security-definer database functions. */
public final class JooqRecoveryTokenStore implements Ports.RecoveryTokenStore {
    private static final String ISSUE_SQL = "SELECT api_schema.issue_recovery_token(?, ?, ?)";
    private static final String COMPLETE_SQL = "SELECT api_schema.consume_recovery_token(?, ?, ?)";
    private final DSLContext dsl;

    public JooqRecoveryTokenStore(final DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext is required");
    }

    @Override
    public void issue(final UserId administratorId, final UserId targetUserId, final RecoveryToken token) {
        dsl.execute(ISSUE_SQL, Objects.requireNonNull(administratorId).value(),
                Objects.requireNonNull(targetUserId).value(), hash(Objects.requireNonNull(token).value()));
    }

    @Override
    public RecoveryCompletion consumeAndReset(final UserId targetUserId, final RecoveryToken token,
                                               final PasswordHash newPasswordHash) {
        final String status = Optional.ofNullable(dsl.fetchOne(COMPLETE_SQL,
                Objects.requireNonNull(targetUserId).value(), hash(Objects.requireNonNull(token).value()),
                Objects.requireNonNull(newPasswordHash).value()))
            .map(record -> record.get(0, String.class))
            .orElse("INVALID_OR_CONSUMED");
        return "COMPLETED".equals(status) ? RecoveryCompletion.COMPLETED : RecoveryCompletion.INVALID_OR_CONSUMED;
    }

    private static String hash(final String token) {
        final byte[] raw = token.getBytes(StandardCharsets.UTF_8);
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw);
            try {
                return HexFormat.of().formatHex(digest);
            } finally {
                java.util.Arrays.fill(digest, (byte) 0);
            }
        } catch (final java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } finally {
            java.util.Arrays.fill(raw, (byte) 0);
        }
    }
}