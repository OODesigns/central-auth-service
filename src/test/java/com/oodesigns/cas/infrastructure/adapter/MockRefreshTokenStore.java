package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.UserId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * In-memory {@link Ports.RefreshTokenStore} for the integration tier (no database required).
 * <p>
 * Faithfully mirrors the production rotation semantics: a token belongs to a family, is consumed
 * on rotation and replaced within the same family, and replaying a consumed/revoked token revokes
 * the entire family (reuse detection).
 */
public final class MockRefreshTokenStore implements Ports.RefreshTokenStore {

    private static final class Entry {
        private final UUID familyId;
        private boolean consumed;
        private boolean revoked;
        private boolean expired;

        private Entry(final UUID familyId) {
            this.familyId = familyId;
        }
    }

    private final Map<String, Entry> tokens = new HashMap<>();

    @Override
    public void issue(final UserId userId, final String refreshToken) {
        Objects.requireNonNull(userId, "UserId cannot be null");
        Objects.requireNonNull(refreshToken, "Refresh token cannot be null");
        tokens.put(refreshToken, new Entry(UUID.randomUUID()));
    }

    @Override
    public RotationStatus rotate(final String presentedToken, final String replacementToken) {
        Objects.requireNonNull(presentedToken, "Presented token cannot be null");
        Objects.requireNonNull(replacementToken, "Replacement token cannot be null");

        final Entry entry = tokens.get(presentedToken);
        if (entry == null) {
            return RotationStatus.NOT_FOUND;
        }
        if (entry.consumed || entry.revoked) {
            revokeFamily(entry.familyId);
            return RotationStatus.REUSE_DETECTED;
        }
        if (entry.expired) {
            entry.revoked = true;
            return RotationStatus.EXPIRED;
        }
        entry.consumed = true;
        entry.revoked = true;
        tokens.put(replacementToken, new Entry(entry.familyId));
        return RotationStatus.ROTATED;
    }

    private void revokeFamily(final UUID familyId) {
        tokens.values().stream()
            .filter(e -> e.familyId.equals(familyId))
            .forEach(e -> e.revoked = true);
    }

    /**
     * Test helper: mark a previously issued token as expired so the next rotation returns
     * {@link RotationStatus#EXPIRED}.
     *
     * @param refreshToken a token previously passed to {@link #issue}
     */
    public void expire(final String refreshToken) {
        final Entry entry = tokens.get(refreshToken);
        if (entry != null) {
            entry.expired = true;
        }
    }

    /**
     * @param refreshToken a token to inspect
     * @return {@code true} if the token exists and has not been consumed or revoked
     */
    public boolean isActive(final String refreshToken) {
        final Entry entry = tokens.get(refreshToken);
        return entry != null && !entry.consumed && !entry.revoked;
    }
}

