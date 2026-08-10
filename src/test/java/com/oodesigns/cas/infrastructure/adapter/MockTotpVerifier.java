package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.BackupCode;
import com.oodesigns.cas.domain.value.TotpCode;
import com.oodesigns.cas.domain.value.UserId;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory mock implementation of {@link Ports.TotpVerifier} for integration tests.
 * <p>
 * Tracks enabled users and counts verification attempts, while allowing tests to preconfigure
 * accepted OTP and backup-code values.
 */
public class MockTotpVerifier implements Ports.TotpVerifier {

    private final Set<UserId> enabledUsers = ConcurrentHashMap.newKeySet();
    private final Set<String> validTotpCodes = ConcurrentHashMap.newKeySet();
    private final Set<String> validBackupCodes = ConcurrentHashMap.newKeySet();
    private final AtomicInteger verificationAttempts = new AtomicInteger();
    private final AtomicInteger backupAttempts = new AtomicInteger();

    @Override
    public boolean verifyCode(final UserId userId, final TotpCode totpCode) {
        verificationAttempts.incrementAndGet();
        if (userId == null || totpCode == null) {
            return false;
        }
        return enabledUsers.contains(userId) && validTotpCodes.contains(totpCode.getCode());
    }

    @Override
    public boolean verifyBackupCode(final UserId userId, final BackupCode backupCode) {
        backupAttempts.incrementAndGet();
        if (userId == null || backupCode == null) {
            return false;
        }
        final String normalized = backupCode.getCode();
        return enabledUsers.contains(userId) && validBackupCodes.remove(normalized);
    }

    @Override
    public boolean isTotpEnabled(final UserId userId) {
        return userId != null && enabledUsers.contains(userId);
    }

    public void enable(final UserId userId) {
        enabledUsers.add(requireUserId(userId));
    }

    public void disable(final UserId userId) {
        enabledUsers.remove(requireUserId(userId));
    }

    public void registerValidTotpCode(final String code) {
        validTotpCodes.add(requireCode(code));
    }

    public void registerValidBackupCode(final String code) {
        validBackupCodes.add(requireCode(code));
    }

    public boolean isRegisteredTotpCode(final String code) {
        return validTotpCodes.contains(requireCode(code));
    }

    public boolean isRegisteredBackupCode(final String code) {
        return validBackupCodes.contains(requireCode(code));
    }

    public int getVerificationAttemptCount() {
        return verificationAttempts.get();
    }

    public int getBackupAttemptCount() {
        return backupAttempts.get();
    }

    public void clear() {
        enabledUsers.clear();
        validTotpCodes.clear();
        validBackupCodes.clear();
        verificationAttempts.set(0);
        backupAttempts.set(0);
    }

    private static UserId requireUserId(final UserId userId) {
        return Objects.requireNonNull(userId, "User ID cannot be null");
    }

    private static String requireCode(final String code) {
        return Objects.requireNonNull(code, "Code cannot be null");
    }
}

