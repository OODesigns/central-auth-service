package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.Ports;
import java.util.Objects;

/** Completes a recovery only after validating the reset-purpose JWT and atomically consuming it. */
public final class CompleteRecoveryCommandHandler {
    private final Ports.TokenVerifier tokenVerifier;
    private final Ports.PasswordHasher passwordHasher;
    private final Ports.RecoveryTokenStore recoveryTokenStore;

    public CompleteRecoveryCommandHandler(final Ports.TokenVerifier tokenVerifier,
                                          final Ports.PasswordHasher passwordHasher,
                                          final Ports.RecoveryTokenStore recoveryTokenStore) {
        this.tokenVerifier = Objects.requireNonNull(tokenVerifier, "Token verifier is required");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "Password hasher is required");
        this.recoveryTokenStore = Objects.requireNonNull(recoveryTokenStore, "Recovery token store is required");
    }

    public CompleteRecoveryResult handle(final CompleteRecoveryCommand command) {
        if (command == null) {
            return CompleteRecoveryResult.failure("INVALID_REQUEST", "Recovery completion request is required");
        }
        final var newPassword = command.newPassword();
        try (newPassword) {
            return tokenVerifier.verifyRecoveryToken(command.token())
                .map(userId -> recoveryTokenStore.consumeAndReset(userId, command.token(), passwordHasher.hash(newPassword)))
                .map(status -> status == Ports.RecoveryTokenStore.RecoveryCompletion.COMPLETED
                    ? CompleteRecoveryResult.success()
                    : CompleteRecoveryResult.failure("INVALID_RECOVERY_TOKEN", "Recovery token is invalid or expired"))
                .orElseGet(() -> CompleteRecoveryResult.failure("INVALID_RECOVERY_TOKEN", "Recovery token is invalid or expired"));
        } catch (final RuntimeException exception) {
            return CompleteRecoveryResult.failure("INTERNAL_ERROR", "Recovery could not be completed");
        }
    }
}