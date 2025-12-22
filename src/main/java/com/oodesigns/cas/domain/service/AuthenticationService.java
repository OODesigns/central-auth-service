package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.value.Jti;
import com.oodesigns.cas.domain.value.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Domain service for authentication logic.
 * Applies business rules independent of transport/persistence.
 */
public final class AuthenticationService {
    private final Ports.PasswordHasher passwordHasher;
    private final Ports.Clock clock;
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    public AuthenticationService(final Ports.PasswordHasher passwordHasher, final Ports.Clock clock) {
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Authenticate a user by verifying password.
     * Returns AuthenticationResult with status.
     */
    public AuthenticationResult authenticate(final User user, final String rawPassword) {
        Objects.requireNonNull(rawPassword, "Password is required");
        
        if (user == null) {
            return AuthenticationResult.failed("User not found");
        }

        if (!passwordHasher.verify(rawPassword, user.getPasswordHash())) {
            return AuthenticationResult.failed("Invalid password");
        }

        return AuthenticationResult.success(user);
    }

    /**
     * Generate tokens for authenticated user, including permissions as claims.
     */
    public TokenPair generateTokens(final User user) {
        Objects.requireNonNull(user, "User is required");
        
        Instant now = clock.now();
        Jti jti = Jti.generate();
        
        String accessToken = createAccessToken(user.getUserId(), jti, user.getPermissions(), now);
        String refreshToken = createRefreshToken(user.getUserId(), now);

        return new TokenPair(accessToken, refreshToken, jti, user.getPermissions());
    }

    private String createAccessToken(final UserId userId, final Jti jti, final java.util.Set<com.oodesigns.cas.domain.value.Permission> permissions, final Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_TTL);
        String permissionsList = "[" + permissions.stream()
            .map(p -> "\"" + p.asString() + "\"")
            .collect(Collectors.joining(",")) + "]";
        String payload = String.format("{\"sub\":\"%s\",\"jti\":\"%s\",\"permissions\":%s,\"iat\":%d,\"exp\":%d}",
                userId.asString(), jti.asString(), permissionsList, issuedAt.getEpochSecond(), expiresAt.getEpochSecond());
        // In real implementation, would use actual token signer
        return "access." + payload;
    }

    private String createRefreshToken(final UserId userId, final Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(REFRESH_TOKEN_TTL);
        String payload = String.format("{\"sub\":\"%s\",\"iat\":%d,\"exp\":%d}",
                userId.asString(), issuedAt.getEpochSecond(), expiresAt.getEpochSecond());
        // In real implementation, would use actual token signer
        return "refresh." + payload;
    }

    /**
     * Result of authentication attempt.
     */
    public static class AuthenticationResult {
        private final boolean success;
        private final User user;
        private final String errorMessage;

        private AuthenticationResult(boolean success, User user, String errorMessage) {
            this.success = success;
            this.user = user;
            this.errorMessage = errorMessage;
        }

        public static AuthenticationResult success(User user) {
            return new AuthenticationResult(true, Objects.requireNonNull(user), null);
        }

        public static AuthenticationResult failed(String errorMessage) {
            return new AuthenticationResult(false, null, Objects.requireNonNull(errorMessage));
        }

        public boolean isSuccess() {
            return success;
        }

        public User getUser() {
            if (!success) throw new IllegalStateException("Authentication failed: " + errorMessage);
            return user;
        }

        public String getErrorMessage() {
            if (success) throw new IllegalStateException("Authentication succeeded");
            return errorMessage;
        }
    }

    /**
     * Token pair (access + refresh).
     */
    public static class TokenPair {
        private final String accessToken;
        private final String refreshToken;
        private final Jti jti;
        private final java.util.Set<com.oodesigns.cas.domain.value.Permission> permissions;

        public TokenPair(String accessToken, String refreshToken, Jti jti, java.util.Set<com.oodesigns.cas.domain.value.Permission> permissions) {
            this.accessToken = Objects.requireNonNull(accessToken);
            this.refreshToken = Objects.requireNonNull(refreshToken);
            this.jti = Objects.requireNonNull(jti);
            this.permissions = java.util.Collections.unmodifiableSet(new java.util.HashSet<>(Objects.requireNonNull(permissions)));
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public Jti getJti() {
            return jti;
        }

        public java.util.Set<com.oodesigns.cas.domain.value.Permission> getPermissions() {
            return permissions;
        }
    }
}
