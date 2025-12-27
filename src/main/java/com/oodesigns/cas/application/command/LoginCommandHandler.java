package com.oodesigns.cas.application.command;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Credentials;
import com.oodesigns.cas.domain.value.UserCredential;

import java.util.Objects;
import java.util.Optional;

/**
 * Application command handler for login.
 * Orchestrates domain services and repositories using two-step authentication:
 * 1. Verify password using lightweight UserCredential
 * 2. Fetch full User object to retrieve permissions for token
 */
public final class LoginCommandHandler {
    private final AuthenticationService authService;
    private final TokenService tokenService;
    private final Ports.UserCredentialReader credentialReader;
    private final Ports.UserRepository userRepository;
    private final Ports.RateLimiter rateLimiter;

    public LoginCommandHandler(final AuthenticationService authService,
                               final TokenService tokenService,
                               final Ports.UserCredentialReader credentialReader,
                               final Ports.UserRepository userRepository,
                               final Ports.RateLimiter rateLimiter) {
        this.authService = Objects.requireNonNull(authService);
        this.tokenService = Objects.requireNonNull(tokenService);
        this.credentialReader = Objects.requireNonNull(credentialReader);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.rateLimiter = Objects.requireNonNull(rateLimiter);
    }

    public LoginResult handle(final LoginCommand command) {
        try {
            return Optional.ofNullable(command)
                .map(this::handleCommand)
                .orElseGet(() -> LoginResult.failure("INVALID_REQUEST", "LoginCommand cannot be null"));
        } catch (final RuntimeException e) {
            return LoginResult.failure("INTERNAL_ERROR", "An internal error occurred during authentication");
        }
    }

    private LoginResult handleCommand(final LoginCommand command) {
        return rateLimiter.checkLimit("login:" + command.ipAddress().asString())
            .mapTo(allowed -> authenticateUser(command))
            .orElse(blocked -> LoginResult.failure("RATE_LIMITED", blocked.message()));
    }

    /**
     * Authenticate user using two-step process:
     * 1. Get credentials (userId + passwordHash) and verify password
     * 2. If valid, fetch full User object for permissions
     * @return LoginResult with success or failure
     */
    private LoginResult authenticateUser(final LoginCommand command) {
        return credentialReader.findCredentialsByUsername(command.username())
            .map(cred -> new Credentials(cred, command.password()))
            .flatMap(authService::getAuthenticatedUser)
            .flatMap(this::fetchFullUserAndGenerateTokens)
            .<LoginResult>map(LoginResult::success)
            .orElseGet(() -> LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password."));
    }

    /**
     * Fetch the full User object by userId and generate tokens.
     * This only executes after password has been verified.
     */
    private Optional<TokenService.TokenPair> fetchFullUserAndGenerateTokens(final UserCredential credential) {
        return userRepository.findById(credential.userId())
            .flatMap(tokenService::generateTokens);
    }
}
