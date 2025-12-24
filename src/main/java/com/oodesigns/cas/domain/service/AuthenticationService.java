package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.value.Jti;
import com.oodesigns.cas.domain.value.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Domain service for authentication logic.
 * Applies business rules independent of transport/persistence.
 */
public final class AuthenticationService {
    private final Ports.PasswordHasher passwordHasher;
    private final Ports.Clock clock;
    private final Ports.TokenSigner tokenSigner;
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    public AuthenticationService(final Ports.PasswordHasher passwordHasher, final Ports.Clock clock, final Ports.TokenSigner tokenSigner) {
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.clock = Objects.requireNonNull(clock);
        this.tokenSigner = Objects.requireNonNull(tokenSigner);
    }

    /**
     * Authenticate a user by verifying password.
     * Returns Optional containing authenticated user if password matches, empty if invalid.
     * Password char array is cleared after verification.
     */
    public Optional<User> getAuthenticatedUser(final User user, final char[] rawPassword) {
        try {
            return Optional.ofNullable(rawPassword)
                    .flatMap(pwd -> Optional.ofNullable(user))
                    .flatMap(u -> verifyPasswordMatch(rawPassword, u));
        } finally {
            clearPassword(rawPassword);
        }
    }

    private void clearPassword(final char[] rawPassword) {
        Optional.ofNullable(rawPassword)
                .ifPresent(pwd -> Arrays.fill(pwd, '\0'));
    }

    private Optional<User> verifyPasswordMatch(final char[] rawPassword, final User user) {
        return passwordHasher.verify(rawPassword, user.passwordHash())
            ? Optional.of(user)
            : Optional.empty();
    }
    /**
     * Generate tokens for authenticated user, including permissions as claims.
     * @return Optional containing TokenPair if tokens generated, empty if user is null
     */
    public Optional<TokenPair> generateTokens(final User user) {
        if (user == null) {
            return Optional.empty();
        }
        
        Instant now = clock.now();
        Jti jti = Jti.generate();
        
        String accessToken = createAccessToken(user.userId(), jti, user.permissions(), now);
        String refreshToken = createRefreshToken(user.userId(), now);

        return Optional.of(new TokenPair(accessToken, refreshToken, jti, user.permissions()));
    }

    private String createAccessToken(final UserId userId, final Jti jti, final java.util.Set<com.oodesigns.cas.domain.value.Permission> permissions, final Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_TTL);
        String permissionsList = "[" + permissions.stream()
            .map(p -> "\"" + p.asString() + "\"")
            .collect(Collectors.joining(",")) + "]";
        String payload = String.format("{\"sub\":\"%s\",\"jti\":\"%s\",\"permissions\":%s,\"iat\":%d,\"exp\":%d}",
                userId.asString(), jti.asString(), permissionsList, issuedAt.getEpochSecond(), expiresAt.getEpochSecond());
        // Sign the token using the injected TokenSigner port
        return tokenSigner.sign(payload, expiresAt);
    }

    private String createRefreshToken(final UserId userId, final Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(REFRESH_TOKEN_TTL);
        String payload = String.format("{\"sub\":\"%s\",\"iat\":%d,\"exp\":%d}",
                userId.asString(), issuedAt.getEpochSecond(), expiresAt.getEpochSecond());
        // Sign the token using the injected TokenSigner port
        return tokenSigner.sign(payload, expiresAt);
    }


    /**
     * Token pair (access + refresh).
     */
    public record TokenPair(String accessToken, String refreshToken, Jti jti,
                            java.util.Set<com.oodesigns.cas.domain.value.Permission> permissions) {
        public TokenPair {
            Objects.requireNonNull(accessToken);
            Objects.requireNonNull(refreshToken);
            Objects.requireNonNull(jti);
            Objects.requireNonNull(permissions);
            // Make permissions unmodifiable
            permissions = java.util.Collections.unmodifiableSet(new java.util.HashSet<>(permissions));
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
