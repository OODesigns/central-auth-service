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
    private static final Duration TOTP_VERIFICATION_TOKEN_TTL = Duration.ofMinutes(5);

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
        final Instant now = clock.now();
        final Jti jti = Jti.generate();
        
        return createAccessToken(user.userId(), jti, user.permissions(), now)
            .flatMap(accessToken -> createRefreshToken(user.userId(), now)
                .map(refreshToken -> new TokenPair(accessToken, refreshToken)));
    }

    private Optional<String> createAccessToken(final UserId userId, final Jti jti,
                                               final java.util.Set<Permission> permissions,
                                               final Instant issuedAt) {
        final Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_TTL);
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


    private Optional<String> getPermissionsList(final Set<Permission> permissions) {
        final String permissionsJson = String.format("[%s]",
                permissions.stream()
                    .map(p -> String.format("\"%s\"", p.toString()))
                    .collect(Collectors.joining(",")));
        return Optional.of(permissionsJson);
    }

    private Optional<String> createRefreshToken(final UserId userId, final Instant issuedAt) {
        final Instant expiresAt = issuedAt.plus(REFRESH_TOKEN_TTL);
        // A unique jti guarantees every refresh token is a distinct credential even when two
        // are issued for the same user within the same second (e.g. rotation), so their hashes
        // never collide in the refresh_tokens table.
        final Jti jti = Jti.generate();

        return createRefreshTokenPayload(userId, jti, issuedAt, expiresAt)
                .flatMap(payload -> tokenSigner.sign(payload, expiresAt));
    }

    private Optional<Payload> createRefreshTokenPayload(final UserId userId,
                                                       final Jti jti,
                                                       final Instant issuedAt,
                                                       final Instant expiresAt){
        // aud:"refresh_token" distinguishes this from access tokens (no aud) and 2FA
        // verification tokens (aud:2fa_verification), preventing token-type confusion.
        return Optional.of(Payload.of(String.format(
                "{\"sub\":\"%s\",\"aud\":\"refresh_token\",\"jti\":\"%s\",\"iat\":%d,\"exp\":%d}",
                userId.toString(), jti.toString(), issuedAt.getEpochSecond(), expiresAt.getEpochSecond())));
    }


    /**
     * Generate a restricted 2FA verification token for 2FA flow.
     * This token is short-lived (5 minutes) and can only be used to verify 2FA codes.
     * Cannot be used for normal API access.
     *
     * @param userId the user who needs to verify 2FA
     * @return the 2FA verification token
     */
    public String generate2FAVerificationToken(final UserId userId) {
        final Instant now = clock.now();
        final Instant expiresAt = now.plus(TOTP_VERIFICATION_TOKEN_TTL);
        final Jti jti = Jti.generate();

        // Minimal payload: sub (user ID), aud (audience for 2FA flow), iat, exp
        final String payloadJson = String.format(
            "{\"sub\":\"%s\",\"aud\":\"2fa_verification\",\"iat\":%d,\"exp\":%d,\"jti\":\"%s\"}",
            userId.toString(),
            now.getEpochSecond(),
            expiresAt.getEpochSecond(),
            jti.value()
        );

        return tokenSigner.sign(Payload.of(payloadJson), expiresAt)
            .orElseThrow(() -> new IllegalStateException("Failed to sign 2FA verification token"));
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
