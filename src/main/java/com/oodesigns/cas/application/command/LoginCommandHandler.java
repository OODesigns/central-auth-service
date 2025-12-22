package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.repository.UserRepository;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Username;

import java.util.Objects;
import java.util.Optional;

/**
 * Application command handler for login.
 * Orchestrates domain services and repositories.
 */
public final class LoginCommandHandler {
    private final UserRepository userRepository;
    private final AuthenticationService authService;
    private final Ports.RateLimiter rateLimiter;

    public LoginCommandHandler(final UserRepository userRepository, final AuthenticationService authService,
                              final Ports.RateLimiter rateLimiter) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.authService = Objects.requireNonNull(authService);
        this.rateLimiter = Objects.requireNonNull(rateLimiter);
    }

    public LoginResult handle(final LoginCommand command) {
        return checkRateLimit(command)
            .orElseGet(() -> authenticateUser(command));
    }

    /**
     * Check if the login attempt from this IP address exceeds rate limit.
     * @return Optional containing failure result if rate limited, empty if OK
     */
    private Optional<LoginResult> checkRateLimit(final LoginCommand command) {
        try {
            rateLimiter.checkLimit("login:" + command.ipAddress());
            return Optional.empty();
        } catch (Ports.RateLimitExceededException e) {
            return Optional.of(LoginResult.failure("RATE_LIMITED", "Too many login attempts. Try again later."));
        }
    }

    /**
     * Authenticate user by finding, verifying password, and generating tokens.
     * @return LoginResult with success or failure
     */
    private LoginResult authenticateUser(final LoginCommand command) {
        return findUserByUsername(command)
            .map(user -> verifyPasswordAndGenerateTokens(user, command))
            .orElseGet(() -> LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password."));
    }

    /**
     * Verify user password and generate tokens if valid.
     * @return LoginResult with success or failure
     */
    private LoginResult verifyPasswordAndGenerateTokens(final User user, final LoginCommand command) {
        return authService.getAuthenticatedUser(user, command.passwordChars())
            .flatMap(this::generateSuccessResult)
            .orElseGet(() -> LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password."));
    }

    /**
     * Find user by username from the command.
     * @return Optional containing User if found, empty Optional otherwise
     */
    private Optional<User> findUserByUsername(final LoginCommand command) {
        return userRepository.findByUsername(Username.of(command.username()));
    }

    /**
     * Generate tokens and build success result for authenticated user.
     * @return Optional containing LoginResult.success if tokens generated, empty if failed
     */
    private Optional<LoginResult> generateSuccessResult(final User authenticatedUser) {
        return authService.generateTokens(authenticatedUser)
            .map(tokens -> LoginResult.success(tokens.getAccessToken(), tokens.getRefreshToken(), 
                                              authenticatedUser.permissions()));
    }
}
