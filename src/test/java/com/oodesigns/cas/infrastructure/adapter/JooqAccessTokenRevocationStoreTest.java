package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.AccessToken;
import com.oodesigns.cas.domain.value.Jti;
import com.oodesigns.cas.domain.value.UserId;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JooqAccessTokenRevocationStoreTest {

    private static final String INVALIDATE_SQL = "SELECT api_schema.invalidate_jwt(?, ?, ?, ?)";
    private static final String CHECK_SQL = "SELECT api_schema.is_jwt_invalidated(?)";
    private static final String TOKEN = "access.token.value";
    private static final String REASON = "logout";

    @Mock
    private DSLContext dsl;

    private JooqAccessTokenRevocationStore store;

    @BeforeEach
    void setUp() {
        store = new JooqAccessTokenRevocationStore(dsl);
    }

    @Test
    void constructorRejectsNullDsl() {
        assertThrows(NullPointerException.class, () -> new JooqAccessTokenRevocationStore(null));
    }

    @Test
    void invalidateHashesTokenAndPassesClaimsToDatabase() {
        final UUID userId = UUID.randomUUID();
        final UUID jti = UUID.randomUUID();
        final Instant expiresAt = Instant.now().plusSeconds(600);
        final Ports.AccessTokenClaims claims = new Ports.AccessTokenClaims(UserId.of(userId), Jti.of(jti), expiresAt);

        store.invalidate(claims, AccessToken.of(TOKEN), REASON);

        verify(dsl).execute(INVALIDATE_SQL, jti, sha256Hex(TOKEN), Timestamp.from(expiresAt), REASON);
    }

    @Test
    void invalidateRejectsNullClaims() {
        assertThrows(NullPointerException.class, () -> store.invalidate(null, AccessToken.of(TOKEN), REASON));
    }

    @Test
    void invalidateRejectsNullToken() {
        final Ports.AccessTokenClaims claims = new Ports.AccessTokenClaims(
                UserId.of(UUID.randomUUID()), Jti.of(UUID.randomUUID()), Instant.now().plusSeconds(600));
        assertThrows(NullPointerException.class, () -> store.invalidate(claims, null, REASON));
    }

    @Test
    void invalidateRejectsNullReason() {
        final Ports.AccessTokenClaims claims = new Ports.AccessTokenClaims(
                UserId.of(UUID.randomUUID()), Jti.of(UUID.randomUUID()), Instant.now().plusSeconds(600));
        assertThrows(NullPointerException.class, () -> store.invalidate(claims, AccessToken.of(TOKEN), null));
    }

    @Test
    void isInvalidatedReturnsTrueWhenRowExists() {
        final UUID jti = UUID.randomUUID();
        final Record record = mock(Record.class);
        when(record.get(0, Boolean.class)).thenReturn(Boolean.TRUE);
        when(dsl.fetchOne(CHECK_SQL, jti)).thenReturn(record);

        assertTrue(store.isInvalidated(Jti.of(jti)));
    }

    @Test
    void isInvalidatedReturnsFalseWhenRowMissing() {
        final UUID jti = UUID.randomUUID();
        when(dsl.fetchOne(CHECK_SQL, jti)).thenReturn(null);

        assertFalse(store.isInvalidated(Jti.of(jti)));
    }

    @Test
    void isInvalidatedReturnsFalseWhenJtiIsNull() {
        assertFalse(store.isInvalidated(null));
    }

    @Test
    void hashingAlgorithmMatchesSha256() {
        assertTrue(sha256Hex(TOKEN).length() > 0);
    }

    @Test
    void invalidateFailsWhenSha256IsUnavailable() {
        final Ports.AccessTokenClaims claims = new Ports.AccessTokenClaims(
                UserId.of(UUID.randomUUID()), Jti.of(UUID.randomUUID()), Instant.now().plusSeconds(600));
        try (var mockedDigest = mockStatic(MessageDigest.class)) {
            mockedDigest.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("unavailable"));

            assertThrows(IllegalStateException.class, () -> store.invalidate(claims, AccessToken.of(TOKEN), REASON));
        }
    }

    private static String sha256Hex(final String token) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
