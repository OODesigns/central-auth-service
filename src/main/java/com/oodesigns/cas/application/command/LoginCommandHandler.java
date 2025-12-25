package com.oodesigns.cas.application.command;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Credentials;

import java.util.Objects;
import java.util.Optional;

/**
 * Application command handler for login.
 * Orchestrates domain services and repositories.
 */
public final class LoginCommandHandler {
    private final AuthenticationService authService;
    private final TokenService tokenService;
    private final Ports.UserRepositoryReader userRepository;
    private final Ports.RateLimiter rateLimiter;

    public LoginCommandHandler(final AuthenticationService authService,
                               final TokenService tokenService,
                               final Ports.UserRepositoryReader userRepository,
                               final Ports.RateLimiter rateLimiter) {
        this.authService = Objects.requireNonNull(authService);
        this.tokenService = Objects.requireNonNull(tokenService);
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
     * Authenticate user by finding, verifying password, and generating tokens.
     * @return LoginResult with success or failure
     */
    private LoginResult authenticateUser(final LoginCommand command) {
        return userRepository.findByUsername(command.username())
            .map(foundUser -> new Credentials(foundUser, command.password()))
            .flatMap(authService::getAuthenticatedUser)
            .flatMap(tokenService::generateTokens)
            .<LoginResult>map(LoginResult::success)
            .orElseGet(() -> LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password."));
    }
}
