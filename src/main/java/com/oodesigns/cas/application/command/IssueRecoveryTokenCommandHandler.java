package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.service.TokenService;
import java.util.Objects;

/** Issues a one-time recovery token after transport authorization has approved the administrator. */
public final class IssueRecoveryTokenCommandHandler {
    private final TokenService tokenService;
    private final Ports.RecoveryTokenStore recoveryTokenStore;

    public IssueRecoveryTokenCommandHandler(final TokenService tokenService,
                                            final Ports.RecoveryTokenStore recoveryTokenStore) {
        this.tokenService = Objects.requireNonNull(tokenService, "Token service is required");
        this.recoveryTokenStore = Objects.requireNonNull(recoveryTokenStore, "Recovery token store is required");
    }

    public IssueRecoveryTokenResult handle(final IssueRecoveryTokenCommand command) {
        if (command == null) {
            return IssueRecoveryTokenResult.failure("INVALID_REQUEST", "Recovery issuance request is required");
        }
        try {
            final var token = tokenService.generateRecoveryToken(command.targetUserId());
            recoveryTokenStore.issue(command.administratorId(), command.targetUserId(), token);
            return IssueRecoveryTokenResult.success(token);
        } catch (final RuntimeException exception) {
            return IssueRecoveryTokenResult.failure("INTERNAL_ERROR", "Recovery token could not be issued");
        }
    }
}