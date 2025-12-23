package com.oodesigns.cas.application.command;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.Ports;

import java.util.Objects;
import java.util.Optional;

/**
 * Application command handler for login.
 * Orchestrates domain services and repositories.
 */
public final class LoginCommandHandler {
    private final AuthenticationService authService;
    private final Ports.UserRepositoryReader userRepository;
    private final Ports.RateLimiter rateLimiter;

    public LoginCommandHandler(final AuthenticationService authService,
                               final Ports.UserRepositoryReader userRepository,
                               final Ports.RateLimiter rateLimiter) {
        this.authService = Objects.requireNonNull(authService);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.rateLimiter = Objects.requireNonNull(rateLimiter);
    }

    public LoginResult handle(final LoginCommand command) {
        Objects.requireNonNull(command, "LoginCommand cannot be null");
        try {
            return checkRateLimit(command)
                .map(this::authenticateUser)
                .orElseGet(() -> LoginResult.failure("RATE_LIMITED", "Rate limit exceeded"));
        } catch (final RuntimeException e) {
            return LoginResult.failure("INTERNAL_ERROR", "An internal error occurred during authentication");
        }
    }

    /**
     * Check if the login attempt from this IP address exceeds rate limit.
     * @return Optional containing the command if allowed, empty if rate limited
     */
    private Optional<LoginCommand> checkRateLimit(final LoginCommand command) {
        return Optional.of(command)
            .filter(cmd -> rateLimiter.checkLimit("login:" + cmd.ipAddress().asString()).isAllowed());
    }

    /**
     * Authenticate user by finding, verifying password, and generating tokens.
     * @return LoginResult with success or failure
     */
    private LoginResult authenticateUser(final LoginCommand command) {
        return userRepository.findByUsername(command.username())
            .flatMap(foundUser -> authService.getAuthenticatedUser(foundUser, command.password().chars()))
            .flatMap(authService::generateTokens)
            .<LoginResult>map(LoginResult::success)
            .orElseGet(() -> LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password."));
    }
}
