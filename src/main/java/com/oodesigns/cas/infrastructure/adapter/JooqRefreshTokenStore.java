package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.UserId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;

/**
 * JOOQ-based implementation of {@link Ports.RefreshTokenStore}.
 * <p>
 * Persists and rotates refresh tokens through SECURITY DEFINER API functions
 * ({@code api_schema.store_refresh_token} / {@code api_schema.rotate_refresh_token}).
 * <p>
 * SECURITY: only a SHA-256 hash of each refresh token is ever sent to the database — the raw
 * token never leaves the process. Refresh tokens are high-entropy signed JWTs, so a fast
 * one-way hash (rather than a slow password hash) is appropriate and enables the unique-index
 * lookup the {@code token_hash} column relies on.
 */
public final class JooqRefreshTokenStore implements Ports.RefreshTokenStore {

    private static final String STORE_SQL = "SELECT api_schema.store_refresh_token(?, ?)";
    private static final String ROTATE_SQL = "SELECT api_schema.rotate_refresh_token(?, ?)";

    private final DSLContext dsl;

    public JooqRefreshTokenStore(final DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext cannot be null");
    }

    @Override
    public void issue(final UserId userId, final String refreshToken) {
        Objects.requireNonNull(userId, "UserId cannot be null");
        Objects.requireNonNull(refreshToken, "Refresh token cannot be null");
        dsl.execute(STORE_SQL, userId.value(), hash(refreshToken));
    }

    @Override
    public RotationStatus rotate(final String presentedToken, final String replacementToken) {
        Objects.requireNonNull(presentedToken, "Presented token cannot be null");
        Objects.requireNonNull(replacementToken, "Replacement token cannot be null");
        final String status = Optional.ofNullable(
                dsl.fetchOne(ROTATE_SQL, hash(presentedToken), hash(replacementToken)))
            .map(record -> record.get(0, String.class))
            .orElse("NOT_FOUND");
        return switch (status) {
            case "ROTATED" -> RotationStatus.ROTATED;
            case "REUSE_DETECTED" -> RotationStatus.REUSE_DETECTED;
            case "EXPIRED" -> RotationStatus.EXPIRED;
            default -> RotationStatus.NOT_FOUND;
        };
    }

    /**
     * SHA-256 hex digest of a refresh token, matching the {@code token_hash} column contents.
     */
    private static String hash(final String token) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] raw = token.getBytes(StandardCharsets.UTF_8);
            try {
                final byte[] hashed = digest.digest(raw);
                try {
                    return HexFormat.of().formatHex(hashed);
                } finally {
                    java.util.Arrays.fill(hashed, (byte) 0);
                }
            } finally {
                java.util.Arrays.fill(raw, (byte) 0);
            }
        } catch (final NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM; this can never happen.
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}

