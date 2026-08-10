package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.application.command.DisableReason;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.BackupCode;
import com.oodesigns.cas.domain.value.UserId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mock implementation of {@link Ports.TotpSetupProvider} for integration tests.
 * <p>
 * Tracks pending secrets, enabled state, and issued backup codes without requiring a database.
 */
public class MockTotpSetupProvider implements Ports.TotpSetupProvider {

    private final Set<UserId> enabledUsers = ConcurrentHashMap.newKeySet();
    private final Set<UserId> disabledUsers = ConcurrentHashMap.newKeySet();
    private final Set<UserId> secretGeneratedUsers = ConcurrentHashMap.newKeySet();
    private final java.util.Map<UserId, String> secrets = new ConcurrentHashMap<>();
    private final java.util.Map<UserId, List<BackupCode>> backupCodesByUser = new ConcurrentHashMap<>();
    private final java.util.Map<UserId, DisableReason> disableReasons = new ConcurrentHashMap<>();

    @Override
    public String generateSecret(final UserId userId) {
        final UserId id = requireUserId(userId);
        final String secret = secretFor(id);
        secrets.put(id, secret);
        secretGeneratedUsers.add(id);
        disabledUsers.remove(id);
        return secret;
    }

    @Override
    public boolean enableTotp(final UserId userId) {
        final UserId id = requireUserId(userId);
        if (enabledUsers.contains(id)) {
            return false;
        }
        enabledUsers.add(id);
        disabledUsers.remove(id);
        return true;
    }

    @Override
    public boolean disableTotp(final UserId userId, final DisableReason reason) {
        final UserId id = requireUserId(userId);
        Objects.requireNonNull(reason, "Disable reason cannot be null");
        final boolean wasEnabled = enabledUsers.remove(id) || secrets.containsKey(id);
        if (wasEnabled) {
            disabledUsers.add(id);
            disableReasons.put(id, reason);
            secrets.remove(id);
            backupCodesByUser.remove(id);
        }
        return wasEnabled;
    }

    @Override
    public List<BackupCode> generateBackupCodes(final UserId userId) {
        final UserId id = requireUserId(userId);
        final List<BackupCode> codes = List.of(
            BackupCode.of("AAAA-BBBB-CCCC-DDDD"),
            BackupCode.of("EEEE-FFFF-GGGG-HHHH"),
            BackupCode.of("IIII-JJJJ-KKKK-LLLL")
        );
        backupCodesByUser.put(id, Collections.unmodifiableList(new ArrayList<>(codes)));
        return codes;
    }

    public Optional<String> secretForUser(final UserId userId) {
        return Optional.ofNullable(secrets.get(requireUserId(userId)));
    }

    public boolean isEnabled(final UserId userId) {
        return enabledUsers.contains(requireUserId(userId));
    }

    public boolean isDisabled(final UserId userId) {
        return disabledUsers.contains(requireUserId(userId));
    }

    public Optional<DisableReason> disableReasonFor(final UserId userId) {
        return Optional.ofNullable(disableReasons.get(requireUserId(userId)));
    }

    public Optional<List<BackupCode>> backupCodesFor(final UserId userId) {
        return Optional.ofNullable(backupCodesByUser.get(requireUserId(userId)));
    }

    public boolean wasSecretGenerated(final UserId userId) {
        return secretGeneratedUsers.contains(requireUserId(userId));
    }

    public void clear() {
        enabledUsers.clear();
        disabledUsers.clear();
        secretGeneratedUsers.clear();
        secrets.clear();
        backupCodesByUser.clear();
        disableReasons.clear();
    }

    private static UserId requireUserId(final UserId userId) {
        return Objects.requireNonNull(userId, "User ID cannot be null");
    }

    private static String secretFor(final UserId userId) {
        final String suffix = userId.value().toString().replace("-", "").substring(0, 16).toUpperCase();
        return (suffix + "AAAAAAAAAAAAAAAA").substring(0, 32);
    }
}

