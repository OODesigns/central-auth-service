package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.value.Jti;
import com.oodesigns.cas.domain.value.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Domain service for token generation.
 * Handles creation of access and refresh tokens with proper claims.
 */
public final class TokenService {
    private final Ports.Clock clock;
    private final Ports.TokenSigner tokenSigner;
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    public TokenService(final Ports.Clock clock, final Ports.TokenSigner tokenSigner) {
        this.clock = Objects.requireNonNull(clock);
        this.tokenSigner = Objects.requireNonNull(tokenSigner);
    }

    /**
     * Generate tokens for authenticated user, including permissions as claims.
     * @return Optional containing TokenPair if tokens generated, empty if user is null
     */
    public Optional<TokenPair> generateTokens(final User user) {
        return Optional.ofNullable(user)
                .map(this::createTokenPairForUser);
    }

    private TokenPair createTokenPairForUser(final User user) {
        Instant now = clock.now();
        Jti jti = Jti.generate();
        
        String accessToken = createAccessToken(user.userId(), jti, user.permissions(), now);
        String refreshToken = createRefreshToken(user.userId(), now);

        return new TokenPair(accessToken, refreshToken, jti, user.permissions());
    }

    private String createAccessToken(final UserId userId, final Jti jti, final java.util.Set<com.oodesigns.cas.domain.value.Permission> permissions, final Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_TTL);
        String permissionsList = "[" + permissions.stream()
            .map(p -> "\"" + p.asString() + "\"")
            .collect(Collectors.joining(",")) + "]";
        String payload = String.format("{\"sub\":\"%s\",\"jti\":\"%s\",\"permissions\":%s,\"iat\":%d,\"exp\":%d}",
                userId.asString(), jti.asString(), permissionsList, issuedAt.getEpochSecond(), expiresAt.getEpochSecond());
        return tokenSigner.sign(com.oodesigns.cas.domain.value.Payload.of(payload), expiresAt);
    }

    private String createRefreshToken(final UserId userId, final Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(REFRESH_TOKEN_TTL);
        String payload = String.format("{\"sub\":\"%s\",\"iat\":%d,\"exp\":%d}",
            userId.asString(), issuedAt.getEpochSecond(), expiresAt.getEpochSecond());
        return tokenSigner.sign(com.oodesigns.cas.domain.value.Payload.of(payload), expiresAt);
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
