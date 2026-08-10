package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.UserId;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mock implementation of {@link Ports.TotpStatusReader} for integration tests.
 * <p>
 * Tracks which users currently have TOTP enabled and returns the user ID only when enabled.
 */
public class MockTotpStatusReader implements Ports.TotpStatusReader {

    private final Set<UserId> enabledUsers = ConcurrentHashMap.newKeySet();

    @Override
    public Optional<UserId> check2FAStatus(final UserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        return enabledUsers.contains(userId) ? Optional.of(userId) : Optional.empty();
    }

    public void enable(final UserId userId) {
        enabledUser(userId, true);
    }

    public void disable(final UserId userId) {
        enabledUser(userId, false);
    }

    public void setEnabled(final UserId userId, final boolean enabled) {
        enabledUser(userId, enabled);
    }

    public boolean isEnabled(final UserId userId) {
        return enabledUsers.contains(requireUserId(userId));
    }

    public void clear() {
        enabledUsers.clear();
    }

    private void enabledUser(final UserId userId, final boolean enabled) {
        final UserId id = requireUserId(userId);
        if (enabled) {
            enabledUsers.add(id);
        } else {
            enabledUsers.remove(id);
        }
    }

    private static UserId requireUserId(final UserId userId) {
        return Objects.requireNonNull(userId, "User ID cannot be null");
    }
}

