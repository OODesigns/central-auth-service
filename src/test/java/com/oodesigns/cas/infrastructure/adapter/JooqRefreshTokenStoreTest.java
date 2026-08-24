package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.RefreshToken;
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
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JooqRefreshTokenStoreTest {

    private static final String STORE_SQL = "SELECT api_schema.store_refresh_token(?, ?)";
    private static final String ROTATE_SQL = "SELECT api_schema.rotate_refresh_token(?, ?)";
    private static final RefreshToken PRESENTED = RefreshToken.of("presented.token.here");
    private static final RefreshToken REPLACEMENT = RefreshToken.of("replacement.token.here");

    @Mock
    private DSLContext dsl;

    private JooqRefreshTokenStore store;

    @BeforeEach
    void setUp() {
        store = new JooqRefreshTokenStore(dsl);
    }

    @Test
    void constructorRejectsNullDsl() {
        assertThrows(NullPointerException.class, () -> new JooqRefreshTokenStore(null));
    }

    @Test
    void issueStoresSha256HashOfToken() {
        final UUID userId = UUID.randomUUID();

        store.issue(UserId.of(userId), PRESENTED);

        verify(dsl).execute(STORE_SQL, userId, sha256Hex(PRESENTED.value()));
    }

    @Test
    void issueRejectsNullUserId() {
        assertThrows(NullPointerException.class, () -> store.issue(null, PRESENTED));
    }

    @Test
    void issueRejectsNullToken() {
        assertThrows(NullPointerException.class, () -> store.issue(UserId.of(UUID.randomUUID()), null));
    }

    @Test
    void rotateHashesBothTokens() {
        stubRotate("ROTATED");

        store.rotate(PRESENTED, REPLACEMENT);

        verify(dsl).fetchOne(ROTATE_SQL, sha256Hex(PRESENTED.value()), sha256Hex(REPLACEMENT.value()));
    }

    @Test
    void rotateMapsRotatedStatus() {
        stubRotate("ROTATED");
        assertEquals(Ports.RefreshTokenStore.RotationStatus.ROTATED, store.rotate(PRESENTED, REPLACEMENT));
    }

    @Test
    void rotateMapsReuseDetectedStatus() {
        stubRotate("REUSE_DETECTED");
        assertEquals(Ports.RefreshTokenStore.RotationStatus.REUSE_DETECTED, store.rotate(PRESENTED, REPLACEMENT));
    }

    @Test
    void rotateMapsExpiredStatus() {
        stubRotate("EXPIRED");
        assertEquals(Ports.RefreshTokenStore.RotationStatus.EXPIRED, store.rotate(PRESENTED, REPLACEMENT));
    }

    @Test
    void rotateMapsNotFoundStatus() {
        stubRotate("NOT_FOUND");
        assertEquals(Ports.RefreshTokenStore.RotationStatus.NOT_FOUND, store.rotate(PRESENTED, REPLACEMENT));
    }

    @Test
    void rotateMapsUnknownStatusToNotFound() {
        stubRotate("SOMETHING_ELSE");
        assertEquals(Ports.RefreshTokenStore.RotationStatus.NOT_FOUND, store.rotate(PRESENTED, REPLACEMENT));
    }

    @Test
    void rotateMapsNullRecordToNotFound() {
        when(dsl.fetchOne(ROTATE_SQL, sha256Hex(PRESENTED.value()), sha256Hex(REPLACEMENT.value()))).thenReturn(null);
        assertEquals(Ports.RefreshTokenStore.RotationStatus.NOT_FOUND, store.rotate(PRESENTED, REPLACEMENT));
    }

    @Test
    void rotateRejectsNullPresentedToken() {
        assertThrows(NullPointerException.class, () -> store.rotate(null, REPLACEMENT));
    }

    @Test
    void rotateRejectsNullReplacementToken() {
        assertThrows(NullPointerException.class, () -> store.rotate(PRESENTED, null));
    }

    @Test
    void hashingThrowsIllegalStateWhenSha256Unavailable() {
        try (var mockedDigest = mockStatic(MessageDigest.class)) {
            mockedDigest.when(() -> MessageDigest.getInstance("SHA-256"))
                .thenThrow(new NoSuchAlgorithmException("boom"));
            assertThrows(IllegalStateException.class,
                () -> store.issue(UserId.of(UUID.randomUUID()), PRESENTED));
        }
    }

    private void stubRotate(final String status) {
        final Record record = mock(Record.class);
        when(record.get(0, String.class)).thenReturn(status);
        when(dsl.fetchOne(ROTATE_SQL, sha256Hex(PRESENTED.value()), sha256Hex(REPLACEMENT.value()))).thenReturn(record);
    }

    private static String sha256Hex(final String token) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

