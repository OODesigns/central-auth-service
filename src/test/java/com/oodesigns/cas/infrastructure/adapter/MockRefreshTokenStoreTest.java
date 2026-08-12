package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
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
        assertEquals(Ports.RefreshTokenStore.RotationStatus.NOT_FOUND, store.rotate("unknown", "new"));
    }

    @Test
    void rotateReturnsRotatedForIssuedToken() {
        store.issue(userId, "t1");
        assertEquals(Ports.RefreshTokenStore.RotationStatus.ROTATED, store.rotate("t1", "t2"));
        assertTrue(store.isActive("t2"));
        assertFalse(store.isActive("t1"));
    }

    @Test
    void rotateChainWorksAcrossMultipleRotations() {
        store.issue(userId, "t1");
        assertEquals(Ports.RefreshTokenStore.RotationStatus.ROTATED, store.rotate("t1", "t2"));
        assertEquals(Ports.RefreshTokenStore.RotationStatus.ROTATED, store.rotate("t2", "t3"));
        assertTrue(store.isActive("t3"));
    }

    @Test
    void replayingConsumedTokenTriggersReuseDetectionAndRevokesFamily() {
        store.issue(userId, "t1");
        store.rotate("t1", "t2");

        assertEquals(Ports.RefreshTokenStore.RotationStatus.REUSE_DETECTED, store.rotate("t1", "tX"));
        // The whole family (including the currently active t2) is now revoked.
        assertFalse(store.isActive("t2"));
        assertEquals(Ports.RefreshTokenStore.RotationStatus.REUSE_DETECTED, store.rotate("t2", "tY"));
    }

    @Test
    void expiredTokenReturnsExpired() {
        store.issue(userId, "t1");
        store.expire("t1");
        assertEquals(Ports.RefreshTokenStore.RotationStatus.EXPIRED, store.rotate("t1", "t2"));
        assertFalse(store.isActive("t1"));
    }

    @Test
    void expireIgnoresUnknownToken() {
        store.expire("nope"); // no exception
        assertFalse(store.isActive("nope"));
    }

    @Test
    void issueRejectsNulls() {
        assertThrows(NullPointerException.class, () -> store.issue(null, "t"));
        assertThrows(NullPointerException.class, () -> store.issue(userId, null));
    }

    @Test
    void rotateRejectsNulls() {
        assertThrows(NullPointerException.class, () -> store.rotate(null, "t"));
        assertThrows(NullPointerException.class, () -> store.rotate("t", null));
    }
}

