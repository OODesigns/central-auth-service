package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.RefreshToken;
import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockRefreshTokenStoreTest {

    private MockRefreshTokenStore store;
    private UserId userId;

    @BeforeEach
    void setUp() {
        store = new MockRefreshTokenStore();
        userId = UserId.of(UUID.randomUUID());
    }

    @Test
    void rotateReturnsNotFoundForUnknownToken() {
        assertEquals(Ports.RefreshTokenStore.RotationStatus.NOT_FOUND, store.rotate(RefreshToken.of("unknown.token.here"), RefreshToken.of("new.token.here")));
    }

    @Test
    void rotateReturnsRotatedForIssuedToken() {
        store.issue(userId, RefreshToken.of("t1.token.here"));
        assertEquals(Ports.RefreshTokenStore.RotationStatus.ROTATED, store.rotate(RefreshToken.of("t1.token.here"), RefreshToken.of("t2.token.here")));
        assertTrue(store.isActive("t2.token.here"));
        assertFalse(store.isActive("t1.token.here"));
    }

    @Test
    void rotateChainWorksAcrossMultipleRotations() {
        store.issue(userId, RefreshToken.of("t1.token.here"));
        assertEquals(Ports.RefreshTokenStore.RotationStatus.ROTATED, store.rotate(RefreshToken.of("t1.token.here"), RefreshToken.of("t2.token.here")));
        assertEquals(Ports.RefreshTokenStore.RotationStatus.ROTATED, store.rotate(RefreshToken.of("t2.token.here"), RefreshToken.of("t3.token.here")));
        assertTrue(store.isActive("t3.token.here"));
    }

    @Test
    void replayingConsumedTokenTriggersReuseDetectionAndRevokesFamily() {
        store.issue(userId, RefreshToken.of("t1.token.here"));
        store.rotate(RefreshToken.of("t1.token.here"), RefreshToken.of("t2.token.here"));

        assertEquals(Ports.RefreshTokenStore.RotationStatus.REUSE_DETECTED, store.rotate(RefreshToken.of("t1.token.here"), RefreshToken.of("tX.token.here")));
        // The whole family (including the currently active t2) is now revoked.
        assertFalse(store.isActive("t2.token.here"));
        assertEquals(Ports.RefreshTokenStore.RotationStatus.REUSE_DETECTED, store.rotate(RefreshToken.of("t2.token.here"), RefreshToken.of("tY.token.here")));
    }

    @Test
    void expiredTokenReturnsExpired() {
        store.issue(userId, RefreshToken.of("t1.token.here"));
        store.expire("t1.token.here");
        assertEquals(Ports.RefreshTokenStore.RotationStatus.EXPIRED, store.rotate(RefreshToken.of("t1.token.here"), RefreshToken.of("t2.token.here")));
        assertFalse(store.isActive("t1.token.here"));
    }

    @Test
    void expireIgnoresUnknownToken() {
        store.expire("nope"); // no exception
        assertFalse(store.isActive("nope"));
    }

    @Test
    void issueRejectsNulls() {
        assertThrows(NullPointerException.class, () -> store.issue(null, RefreshToken.of("t.token.here")));
        assertThrows(NullPointerException.class, () -> store.issue(userId, null));
    }

    @Test
    void rotateRejectsNulls() {
        assertThrows(NullPointerException.class, () -> store.rotate(null, RefreshToken.of("t.token.here")));
        assertThrows(NullPointerException.class, () -> store.rotate(RefreshToken.of("t.token.here"), null));
    }
}

