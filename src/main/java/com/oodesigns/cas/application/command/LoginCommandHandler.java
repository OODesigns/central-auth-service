package com.oodesigns.cas.application.command;
import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Credentials;
import com.oodesigns.cas.domain.value.UserId;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application command handler for login.
 * Orchestrates domain services and repositories using two-step authentication:
 * 1. Verify password using lightweight UserCredential
 * 2. Fetch full User object to retrieve permissions for token
 */
public final class LoginCommandHandler {
    private static final Logger LOGGER = Logger.getLogger(LoginCommandHandler.class.getName());
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    
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
            LOGGER.log(Level.SEVERE, INTERNAL_ERROR, e);
            return LoginResult.failure(INTERNAL_ERROR, e.getMessage());
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
     * 2. If valid, fetch full User object for permissions and generate tokens
     * @return LoginResult with success or failure
     */
    private LoginResult authenticateUser(final LoginCommand command) {
        return credentialReader.findCredentialsByUsername(command.username())
            .map(cred -> new Credentials(cred, command.password()))
            .flatMap(authService::getAuthenticatedUser)
            .flatMap(this::fetchFullUserAndGenerateTokens)
            .<LoginResult>map(pair -> LoginResult.success(pair.tokenPair(), pair.user().userId(), pair.user().permissions()))
            .orElseGet(() -> LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password."));
    }

    /**
     * Fetch the full User object by userId and generate tokens.
     * This only executes after password has been verified.
     * Returns a TokenAndUserPair containing both the tokens and authenticated user.
     */
    private Optional<TokenAndUserPair> fetchFullUserAndGenerateTokens(final UserId userId) {
        return userRepository.findById(userId)
            .flatMap(user -> tokenService.generateTokens(user)
                .map(tokens -> new TokenAndUserPair(tokens, user)));
    }

    /**
     * Immutable utility record for carrying both TokenPair and User through the Optional chain.
     * 
     * Used to transport both the generated TokenPair and the authenticated User object
     * from token generation back to the login handler without losing the User data.
     * 
     * This is necessary because TokenService.generateTokens() returns only the TokenPair,
     * but we need both the tokens AND the User's permissions/userId for the response.
     * 
     * @param tokenPair the generated access and refresh tokens (must not be null)
     * @param user the authenticated user with permissions (must not be null)
     */
    private record TokenAndUserPair(TokenService.TokenPair tokenPair, User user) {
        /**
         * Compact constructor validates both values are non-null.
         * 
         * @throws NullPointerException if either tokenPair or user is null
         */
        public TokenAndUserPair {
            Objects.requireNonNull(tokenPair, "TokenPair cannot be null");
            Objects.requireNonNull(user, "User cannot be null");
        }
    }
}
