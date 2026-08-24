package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Credentials;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Executes administrative TOTP disablement after admin reauthentication. */
public final class AdminDisableTotpCommandHandler {
    private static final Logger LOGGER = Logger.getLogger(AdminDisableTotpCommandHandler.class.getName());
    private static final String INVALID_PASSWORD = "INVALID_PASSWORD";
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private final AuthenticationService authenticationService;
    private final Ports.UserCredentialByIdRetriever credentialReader;
    private final Ports.TotpSetupProvider totpSetupProvider;

    public AdminDisableTotpCommandHandler(final AuthenticationService authenticationService,
                                          final Ports.UserCredentialByIdRetriever credentialReader,
                                          final Ports.TotpSetupProvider totpSetupProvider) {
        this.authenticationService = Objects.requireNonNull(authenticationService);
        this.credentialReader = Objects.requireNonNull(credentialReader);
        this.totpSetupProvider = Objects.requireNonNull(totpSetupProvider);
    }

    public DisableTotpResult handle(final AdminDisableTotpCommand command) {
        try {
            return reauthenticate(command)
                ? disableTarget(command)
                : DisableTotpResult.failure(INVALID_PASSWORD, "Administrator reauthentication failed.");
        } catch (final RuntimeException exception) {
            LOGGER.log(Level.SEVERE, INTERNAL_ERROR, exception);
            return DisableTotpResult.failure(INTERNAL_ERROR, "2FA could not be disabled.");
        }
    }

    private boolean reauthenticate(final AdminDisableTotpCommand command) {
        return credentialReader.findCredentialsByUserId(command.adminId())
            .map(credential -> Credentials.of(credential, command.adminPassword()))
            .flatMap(authenticationService::getAuthenticatedUser)
            .filter(command.adminId()::equals)
            .isPresent();
    }

    private DisableTotpResult disableTarget(final AdminDisableTotpCommand command) {
        return totpSetupProvider.disableTotp(command.targetUserId(), command.reason())
            ? DisableTotpResult.success()
            : DisableTotpResult.failure("TOTP_NOT_ENABLED", "Target user does not have 2FA enabled.");
    }
}