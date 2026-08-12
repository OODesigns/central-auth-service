package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.value.UserId;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application command handler for refresh-token rotation.
 * <p>
 * Exchanges a valid refresh token for a fresh access + refresh token pair, rotating the
 * presented token (rotating refresh tokens with automatic reuse detection).
 * <p>
 * Flow (security-ordered):
 * <ol>
 *   <li>Verify the refresh token JWT (signature, expiry, {@code aud: refresh_token}) via
 *       {@link Ports.TokenVerifier#verifyRefreshToken}. Fails with {@code INVALID_REFRESH_TOKEN}.</li>
 *   <li>Load the full user (permissions) via {@link Ports.UserRetriever}. Fails with
 *       {@code USER_NOT_FOUND}.</li>
 *   <li>Generate a new access + refresh token pair via {@link TokenService#generateTokens}.
 *       Fails with {@code INTERNAL_ERROR} if signing fails.</li>
 *   <li>Atomically rotate via {@link Ports.RefreshTokenStore#rotate}. The store is the
 *       authoritative source of truth for whether the presented token is still current:
 *       <ul>
 *         <li>{@code ROTATED} → success.</li>
 *         <li>{@code REUSE_DETECTED} → {@code REFRESH_TOKEN_REUSE_DETECTED} (family revoked).</li>
 *         <li>{@code NOT_FOUND} → {@code INVALID_REFRESH_TOKEN}.</li>
 *         <li>{@code EXPIRED} → {@code REFRESH_TOKEN_EXPIRED}.</li>
 *       </ul>
 *   </li>
 * </ol>
 * <p>
 * SECURITY: the new token pair is generated before rotation is confirmed, but it is never
 * persisted unless {@code rotate} returns {@code ROTATED}. A rejected rotation therefore
 * discards the freshly generated (never-stored) tokens.
 */
public final class RefreshTokenCommandHandler {

    private static final Logger LOGGER = Logger.getLogger(RefreshTokenCommandHandler.class.getName());
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    private static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";

    private final Ports.TokenVerifier tokenVerifier;
    private final Ports.UserRetriever userRetriever;
    private final TokenService tokenService;
    private final Ports.RefreshTokenStore refreshTokenStore;

    public RefreshTokenCommandHandler(final Ports.TokenVerifier tokenVerifier,
                                      final Ports.UserRetriever userRetriever,
                                      final TokenService tokenService,
                                      final Ports.RefreshTokenStore refreshTokenStore) {
        this.tokenVerifier = Objects.requireNonNull(tokenVerifier, "TokenVerifier is required");
        this.userRetriever = Objects.requireNonNull(userRetriever, "UserRetriever is required");
        this.tokenService = Objects.requireNonNull(tokenService, "TokenService is required");
        this.refreshTokenStore = Objects.requireNonNull(refreshTokenStore, "RefreshTokenStore is required");
    }

    /**
     * Handle the refresh command.
     *
     * @param command the command carrying the refresh token; {@code null} returns an
     *                {@code INVALID_REQUEST} failure without touching any port
     * @return {@link RefreshTokenResult} with a fresh token pair on success, or a failure
     */
    public RefreshTokenResult handle(final RefreshTokenCommand command) {
        try {
            return Optional.ofNullable(command)
                .map(this::rotate)
                .orElseGet(() -> RefreshTokenResult.failure("INVALID_REQUEST",
                    "RefreshTokenCommand cannot be null"));
        } catch (final RuntimeException e) {
            LOGGER.log(Level.SEVERE, INTERNAL_ERROR, e);
            return RefreshTokenResult.failure(INTERNAL_ERROR, "Refresh failed: " + e.getMessage());
        }
    }

    private RefreshTokenResult rotate(final RefreshTokenCommand command) {
        // Step 1: Validate the refresh token JWT (signature, expiry, audience).
        final Optional<UserId> userIdOpt = tokenVerifier.verifyRefreshToken(command.refreshToken());
        if (userIdOpt.isEmpty()) {
            return RefreshTokenResult.failure(INVALID_REFRESH_TOKEN,
                "The refresh token is expired or invalid. Please log in again.");
        }
        final UserId userId = userIdOpt.get();

        // Step 2: Load user (needed for permission claims in the new access token).
        final Optional<User> userOpt = userRetriever.findById(userId);
        if (userOpt.isEmpty()) {
            return RefreshTokenResult.failure("USER_NOT_FOUND", "User account could not be located.");
        }
        final User user = userOpt.get();

        // Step 3: Generate the replacement token pair (not yet persisted).
        final Optional<TokenService.TokenPair> tokensOpt = tokenService.generateTokens(user);
        if (tokensOpt.isEmpty()) {
            return RefreshTokenResult.failure(INTERNAL_ERROR, "Failed to generate tokens.");
        }
        final TokenService.TokenPair tokens = tokensOpt.get();

        // Step 4: Atomically rotate — the store decides if the presented token is still current.
        final Ports.RefreshTokenStore.RotationStatus status =
            refreshTokenStore.rotate(command.refreshToken(), tokens.refreshToken());
        return switch (status) {
            case ROTATED -> RefreshTokenResult.success(tokens, userId, user.permissions());
            case REUSE_DETECTED -> RefreshTokenResult.failure("REFRESH_TOKEN_REUSE_DETECTED",
                "Refresh token reuse detected. All sessions in this family have been revoked. Please log in again.");
            case EXPIRED -> RefreshTokenResult.failure("REFRESH_TOKEN_EXPIRED",
                "The refresh token has expired. Please log in again.");
            case NOT_FOUND -> RefreshTokenResult.failure(INVALID_REFRESH_TOKEN,
                "The refresh token is not recognised. Please log in again.");
        };
    }
}

