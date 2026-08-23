package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Jti;
import org.jooq.DSLContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * JOOQ-based implementation of {@link Ports.AccessTokenRevocationStore}.
 */
public final class JooqAccessTokenRevocationStore implements Ports.AccessTokenRevocationStore {

    private static final String INVALIDATE_SQL = "SELECT api_schema.invalidate_jwt(?, ?, ?, ?)";
    private static final String CHECK_SQL = "SELECT api_schema.is_jwt_invalidated(?)";

    private final DSLContext dsl;

    public JooqAccessTokenRevocationStore(final DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext cannot be null");
    }

    @Override
    public void invalidate(final Ports.AccessTokenClaims claims, final String token, final String reason) {
        Objects.requireNonNull(claims, "AccessTokenClaims cannot be null");
        Objects.requireNonNull(token, "Token cannot be null");
        Objects.requireNonNull(reason, "Reason cannot be null");
        dsl.execute(INVALIDATE_SQL,
                claims.jti().asUUID(),
                hash(token),
                Timestamp.from(claims.expiresAt()),
                reason);
    }

    @Override
    public boolean isInvalidated(final Jti jti) {
        return Optional.ofNullable(jti)
                .flatMap(value -> Optional.ofNullable(dsl.fetchOne(CHECK_SQL, value.asUUID())))
                .map(record -> record.get(0, Boolean.class))
                .orElse(false);
    }

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
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}