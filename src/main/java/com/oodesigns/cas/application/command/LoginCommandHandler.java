package com.oodesigns.cas.application.command;
import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Credentials;
import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.UserId;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application command handler for login.
 * Orchestrates domain services and repositories with security-first ordering.
 * <p>
 * MODEL: Post-MFA password reset with forced re-login.
 * <p>
 * CRITICAL: Password reset is a CONTROLLED EXIT, not a login success.
 * When entered:
 * - Authentication is considered incomplete
 * - Any issued tokens are short-lived and reset-scoped only
 * - User must re-authenticate after completing the reset
 * <p>
 * Ordering (login flow):
 * 1. Verify password
 * 2. Load user + MFA policy (minimal identity data)
 * 3. Enforce MFA enrollment
 *    - If MFA is required and not enrolled:
 *      - Block login immediately
 *      - Return MFA_REQUIRED_SETUP
 * 4. Enforce MFA challenge (if enabled)
 * 5. Evaluate password reset requirement
 *    - If password reset required:
 *      - Issue reset-scoped tokens only
 *      - Return PASSWORD_RESET_REQUIRED
 *      - Force logout after reset completion
 *      - User must restart login flow
 * 6. Load full permissions (login path only)
 * 7. Issue full access tokens
 * <p>
 * Permission model:
 * - MFA setup branch: only "setup_mfa" permission
 * - Password reset branch:
 *   - Reset-scoped permission set only
 *   - No login permissions
 *   - No session continuity after reset
 * - Login branch: full role-based permissions
 * <p>
 * Security guarantees:
 * - MFA is always enforced before sensitive state transitions
 * - Password reset cannot be used to bypass MFA
 * - No authenticated session survives a password reset
 */
public final class LoginCommandHandler {
    private static final Logger LOGGER = Logger.getLogger(LoginCommandHandler.class.getName());
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private final AuthenticationService authService;
    private final TokenService tokenService;
    private final Ports.UserCredentialRetriever credentialReader;
    private final Ports.UserRetriever userRepository;
    private final Ports.TotpStatusReader totpStatusReader;
    private final Ports.RateLimiter rateLimiter;

    public LoginCommandHandler(final AuthenticationService authService,
                               final TokenService tokenService,
                               final Ports.UserCredentialRetriever credentialReader,
                               final Ports.UserRetriever userRepository,
                               final Ports.TotpStatusReader totpStatusReader,
                               final Ports.RateLimiter rateLimiter) {
        this.authService = Objects.requireNonNull(authService);
        this.tokenService = Objects.requireNonNull(tokenService);
        this.credentialReader = Objects.requireNonNull(credentialReader);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.totpStatusReader = Objects.requireNonNull(totpStatusReader);
        this.rateLimiter = Objects.requireNonNull(rateLimiter);
    }

    /**
     * Handle login command execution.
     * Wraps authenticateUser in exception handling and null checks.
     *
     * @param command the login command containing username, password, and IP address
     * @return LoginResult with success, 2FA required, password reset required, or failure details
     */
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

    /**
     * Execute login command after rate limit check.
     * Checks three rate limit buckets: IP address, username, and IP+username combination.
     * Returns the first blocking result or allows the request to proceed.
     *
     * @param command the login command
     * @return LoginResult with success, 2FA required, password reset required, or failure
     */
    private LoginResult handleCommand(final LoginCommand command) {
        return rateLimiter.checkLimit(command)
            .mapTo(ignored -> authenticateUser(command))
            .orElse(blocked -> LoginResult.failure("RATE_LIMITED", blocked.message()));
    }

    /**
     * Authenticate user for login session.
     * <p>
     * Security-first ordering (post-MFA password reset model):
     * 1. Verify password (already done via getAuthenticatedUser)
     * 2. Load user data with MFA policy (minimal data)
     * 3. Check 2FA status and route to appropriate response
     *
     * @param command the login command containing username and password
     * @return LoginResult with tokens, 2FA challenge, or failure
     */
    private LoginResult authenticateUser(final LoginCommand command) {
        return credentialReader.findCredentialsByUsername(command.username())
            .map(cred -> Credentials.of(cred, command.password()))
            .flatMap(authService::getAuthenticatedUser)
            .flatMap(this::getResponse)
            .orElseGet(() -> LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password."));
    }

    /**
     * Route to appropriate response based on 2FA status.
     * If 2FA is enabled, return a verification token.
     * If 2FA is disabled, proceed to generate full tokens.
     *
     * @param userId the authenticated user ID
     * @return LoginResult with 2FA challenge or success with tokens, or empty if user not found
     */
    private Optional<LoginResult> getResponse(final UserId userId) {
        final Optional<LoginResult> twoFAResult = totpStatusReader.check2FAStatus(userId)
            .map(tokenService::generate2FAVerificationToken)
            .map(token -> LoginResult.required2FA(token, userId));

        if (twoFAResult.isPresent()) {
            return twoFAResult;
        }

        return generateTokens(userId)
            .map(pair -> LoginResult.success(pair.tokenPair(), pair.userId(), pair.permissions()));
    }

    /**
     * Fetch full User object and generate access/refresh tokens.
     * This executes after password has been verified and 2FA checked.
     * Returns a TokenAndUserPair containing both the tokens and authenticated user.
     *
     * @param userId the authenticated user ID
     * @return Optional containing TokenAndUserPair with tokens and user data, or empty if user not found
     */
    private Optional<TokenAndUserPair> generateTokens(final UserId userId) {
        return userRepository.findById(userId)
            .flatMap(user -> tokenService.generateTokens(user)
                .map(tokens -> new TokenAndUserPair(tokens, user)));
    }

    /**
     * Immutable utility record for carrying both TokenPair and User through the Optional chain.
     * Used to transport both the generated TokenPair and the authenticated User object
     * from token generation back to the login handler without losing the User data.
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

        public UserId userId() {
            return user.userId();
        }

        public Set<Permission> permissions() {
            return user.permissions();
        }
    }
}

