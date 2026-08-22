package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.Ports;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application command handler for access-token logout / revocation.
 */
public final class LogoutCommandHandler {

    private static final Logger LOGGER = Logger.getLogger(LogoutCommandHandler.class.getName());
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private final Ports.TokenVerifier tokenVerifier;
    private final Ports.AccessTokenRevocationStore revocationStore;

    public LogoutCommandHandler(final Ports.TokenVerifier tokenVerifier,
                                final Ports.AccessTokenRevocationStore revocationStore) {
        this.tokenVerifier = Objects.requireNonNull(tokenVerifier, "TokenVerifier is required");
        this.revocationStore = Objects.requireNonNull(revocationStore, "AccessTokenRevocationStore is required");
    }

    public LogoutResult handle(final LogoutCommand command) {
        try {
            return Optional.ofNullable(command)
                    .map(this::logout)
                    .orElseGet(() -> LogoutResult.failure("INVALID_REQUEST", "LogoutCommand cannot be null"));
        } catch (final RuntimeException e) {
            LOGGER.log(Level.SEVERE, INTERNAL_ERROR, e);
            return LogoutResult.failure(INTERNAL_ERROR, "Logout failed: " + e.getMessage());
        }
    }

    private LogoutResult logout(final LogoutCommand command) {
        final Optional<Ports.AccessTokenClaims> claimsOpt = tokenVerifier.verifyAccessToken(command.accessToken());
        if (claimsOpt.isEmpty()) {
            return LogoutResult.failure("INVALID_ACCESS_TOKEN",
                    "The access token is expired, revoked, or invalid. Please log in again.");
        }
        revocationStore.invalidate(claimsOpt.get(), command.accessToken(), "logout");
        return LogoutResult.success();
    }
}