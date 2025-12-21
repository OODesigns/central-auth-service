package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.repository.UserRepository;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.AuthenticationService.AuthenticationResult;
import com.oodesigns.cas.domain.service.AuthenticationService.TokenPair;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Username;

import java.util.Objects;

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
        try {
            // Rate limiting by IP
            rateLimiter.checkLimit("login:" + command.getIpAddress());
        } catch (Ports.RateLimitExceededException e) {
            return LoginResult.failure("RATE_LIMITED", "Too many login attempts. Try again later.");
        }

        try {
            // Find user by username
            Username username = new Username(command.getUsername());
            User user = userRepository.findByUsername(username).orElse(null);

            // Authenticate
            AuthenticationResult authResult = authService.authenticate(user, 
                    new String(command.getPasswordChars()));

            if (!authResult.isSuccess()) {
                // Don't expose which field is wrong
                return LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password.");
            }

            User authenticatedUser = authResult.getUser();

            // Generate tokens (includes permissions as JWT claims)
            TokenPair tokens = authService.generateTokens(authenticatedUser);

            return LoginResult.success(tokens.getAccessToken(), tokens.getRefreshToken(), authenticatedUser.getPermissions());

        } catch (IllegalArgumentException e) {
            return LoginResult.failure("INVALID_REQUEST", "Invalid username or password.");
        } catch (Exception e) {
            return LoginResult.failure("INTERNAL_ERROR", "An unexpected error occurred.");
        }
    }
}
