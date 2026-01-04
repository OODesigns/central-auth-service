package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.value.Jti;
import com.oodesigns.cas.domain.value.Payload;
import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
                .flatMap(this::createTokenPairForUser);
    }

    private Optional<TokenPair> createTokenPairForUser(final User user) {
        Instant now = clock.now();
        Jti jti = Jti.generate();
        
        return createAccessToken(user.userId(), jti, user.permissions(), now)
            .flatMap(accessToken -> createRefreshToken(user.userId(), now)
                .map(refreshToken -> new TokenPair(accessToken, refreshToken)));
    }

    private Optional<String> createAccessToken(final UserId userId, final Jti jti,
                                               final java.util.Set<Permission> permissions,
                                               final Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_TTL);
        return getPermissionsList(permissions)
                .flatMap(p -> createAccessTokenPayload(userId, jti, p, issuedAt, expiresAt))
                .flatMap(payload -> tokenSigner.sign(payload, expiresAt));
    }

    private Optional<Payload> createAccessTokenPayload(final UserId userId,
                                                       final Jti jti,
                                                       final String permissionsList,
                                                       final Instant issuedAt,
                                                       final Instant expiresAt){
        return Optional.of(Payload.of(String.format("{\"sub\":\"%s\",\"jti\":\"%s\",\"permissions\":%s,\"iat\":%d,\"exp\":%d}",
                userId.toString(), jti.toString(), permissionsList, issuedAt.getEpochSecond(), expiresAt.getEpochSecond())));
    }


    private Optional<String> getPermissionsList(Set<Permission> permissions) {
        String permissionsJson = String.format("[%s]",
                permissions.stream()
                    .map(p -> String.format("\"%s\"", p.toString()))
                    .collect(Collectors.joining(",")));
        return Optional.of(permissionsJson);
    }

    private Optional<String> createRefreshToken(final UserId userId, final Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(REFRESH_TOKEN_TTL);

        return createRefreshTokenPayload(userId, issuedAt, expiresAt)
                .flatMap(payload -> tokenSigner.sign(payload, expiresAt));
    }

    private Optional<Payload> createRefreshTokenPayload(final UserId userId, 
                                                       final Instant issuedAt,
                                                       final Instant expiresAt){
        return Optional.of(Payload.of(String.format("{\"sub\":\"%s\",\"iat\":%d,\"exp\":%d}",
                userId.toString(), issuedAt.getEpochSecond(), expiresAt.getEpochSecond())));
    }


    /**
     * Token pair (access + refresh).
     */
    public record TokenPair(String accessToken, String refreshToken) {
        public TokenPair {
            Objects.requireNonNull(accessToken);
            Objects.requireNonNull(refreshToken);
        }

    }
}
