package com.oodesigns.cas.infrastructure.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Jti;
import com.oodesigns.cas.domain.value.KeyPassword;
import com.oodesigns.cas.domain.value.UserId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Production implementation of {@link Ports.TokenVerifier} using JJWT.
 * <p>
 * Verifies 2FA verification tokens issued by {@link JwtTokenSigner}:
 * <ol>
 *   <li>Parses the JWT and verifies the HMAC-SHA256 signature.</li>
 *   <li>JJWT automatically rejects expired tokens ({@code exp} claim).</li>
 *   <li>Extracts the nested {@code payload} JSON string and checks {@code aud == "2fa_verification"}.</li>
 *   <li>Returns the {@code sub} claim as a {@link UserId}.</li>
 * </ol>
 * <p>
 * Token structure produced by {@link JwtTokenSigner}:
 * <pre>
 * JWT body: { "payload": "{\"sub\":\"...\",\"aud\":\"2fa_verification\",...}", "exp": ... }
 * </pre>
 * The domain claims (sub, aud, iat, jti) are embedded inside the {@code payload} string,
 * not as top-level JWT claims.
 */
public final class JwtTokenVerifier implements Ports.TokenVerifier {

    private static final Logger LOGGER = Logger.getLogger(JwtTokenVerifier.class.getName());
    private static final String AUDIENCE_2FA = "2fa_verification";
    private static final String AUDIENCE_REFRESH = "refresh_token";
    private static final String AUDIENCE_ACCESS = "access_token";
    private static final int TOKEN_VERSION = 2;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final KeySupplier keySupplier;
    private final List<String> keyIds;
    private final ObjectMapper objectMapper;
    private final Ports.AccessTokenRevocationStore accessTokenRevocationStore;

    /**
     * @param keySupplier supplies the signing key (same key used by {@link JwtTokenSigner})
     * @param keyId       environment variable / key identifier for the signing key
     */
    public JwtTokenVerifier(final KeySupplier keySupplier,
                            final String keyId,
                            final Ports.AccessTokenRevocationStore accessTokenRevocationStore) {
        this(keySupplier, List.of(keyId), new ObjectMapper(), accessTokenRevocationStore);
    }

    public JwtTokenVerifier(final KeySupplier keySupplier,
                            final List<String> keyIds,
                            final Ports.AccessTokenRevocationStore accessTokenRevocationStore) {
        this(keySupplier, keyIds, new ObjectMapper(), accessTokenRevocationStore);
    }

    JwtTokenVerifier(final KeySupplier keySupplier,
                     final String keyId,
                     final ObjectMapper objectMapper,
                     final Ports.AccessTokenRevocationStore accessTokenRevocationStore) {
        this(keySupplier, List.of(keyId), objectMapper, accessTokenRevocationStore);
    }

    private JwtTokenVerifier(final KeySupplier keySupplier,
                     final List<String> keyIds,
                     final ObjectMapper objectMapper,
                     final Ports.AccessTokenRevocationStore accessTokenRevocationStore) {
        this.keySupplier = Objects.requireNonNull(keySupplier, "KeySupplier cannot be null");
        Objects.requireNonNull(keyIds, "Key IDs cannot be null");
        this.keyIds = keyIds.stream()
            .map(keyId -> Objects.requireNonNull(keyId, "Key ID cannot be null"))
            .filter(keyId -> !keyId.isBlank())
            .distinct()
            .toList();
        if (this.keyIds.isEmpty()) {
            throw new IllegalArgumentException("At least one verification key ID is required");
        }
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
        this.accessTokenRevocationStore = Objects.requireNonNull(accessTokenRevocationStore, "AccessTokenRevocationStore cannot be null");
    }

    @Override
    public Optional<Ports.AccessTokenClaims> verifyAccessToken(final String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return keyIds.stream()
            .map(keySupplier::getPassword)
                .flatMap(password -> password.stream())
            .map(password -> parseAndVerifyAccessToken(token, password))
                .flatMap(claims -> claims.stream())
            .findFirst();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns {@link Optional#empty()} when the token is {@code null}, blank, expired,
     * has an invalid signature, has the wrong audience, or cannot be parsed.
     */
    @Override
    public Optional<UserId> verify2FAVerificationToken(final String token) {
        return verifyWithAudience(token, AUDIENCE_2FA);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns {@link Optional#empty()} when the token is {@code null}, blank, expired,
     * has an invalid signature, has an audience other than {@code "refresh_token"}, or
     * cannot be parsed.
     */
    @Override
    public Optional<UserId> verifyRefreshToken(final String token) {
        return verifyWithAudience(token, AUDIENCE_REFRESH);
    }

    private Optional<UserId> verifyWithAudience(final String token, final String expectedAudience) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return keyIds.stream()
            .map(keySupplier::getPassword)
                .flatMap(password -> password.stream())
            .map(password -> parseAndVerify(token, password, expectedAudience))
                .flatMap(userId -> userId.stream())
            .findFirst();
    }

    private Optional<Ports.AccessTokenClaims> parseAndVerifyAccessToken(final String token, final KeyPassword password) {
        try (password) {
            final SecretKey key = Keys.hmacShaKeyFor(password.toUtf8Bytes());
            final Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (isVersionTwo(claims)) {
                return extractVersionTwoAccessTokenClaims(claims);
            }
            final String payloadJson = claims.get("payload", String.class);
            return extractAccessTokenClaims(payloadJson, claims.getExpiration());
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, "Failed to verify access token", e);
            return Optional.empty();
        }
    }

    private Optional<UserId> parseAndVerify(final String token, final KeyPassword password,
                                            final String expectedAudience) {
        try (password) {
            final SecretKey key = Keys.hmacShaKeyFor(password.toUtf8Bytes());
            final Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (isVersionTwo(claims)) {
                return extractVersionTwoUserId(claims, expectedAudience);
            }
            final String payloadJson = claims.get("payload", String.class);
            return extractUserId(payloadJson, expectedAudience);
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, () -> "Failed to verify token: " + e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<UserId> extractUserId(final String payloadJson, final String expectedAudience) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Optional.empty();
        }
        try {
            final Map<String, Object> payload = objectMapper.readValue(payloadJson, MAP_TYPE);
            final String aud = (String) payload.get("aud");
            if (!expectedAudience.equals(aud)) {
                return Optional.empty();
            }
            final String sub = (String) payload.get("sub");
            if (sub == null || sub.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(UserId.of(sub));
        } catch (final Exception e) {
            LOGGER.log(Level.FINE, () -> "Failed to parse token payload: " + e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isVersionTwo(final Claims claims) {
        final Number version = claims.get("ver", Number.class);
        return version != null && version.intValue() == TOKEN_VERSION;
    }

    private Optional<UserId> extractVersionTwoUserId(final Claims claims, final String expectedAudience) {
        if (!hasAudience(claims, expectedAudience)) {
            return Optional.empty();
        }
        final String subject = claims.getSubject();
        return subject == null || subject.isBlank()
            ? Optional.empty()
            : Optional.of(UserId.of(subject));
    }

    private Optional<Ports.AccessTokenClaims> extractVersionTwoAccessTokenClaims(final Claims claims) {
        if (!hasAudience(claims, AUDIENCE_ACCESS)
                || claims.getSubject() == null || claims.getSubject().isBlank()
                || claims.getId() == null || claims.getId().isBlank()
                || claims.getExpiration() == null) {
            return Optional.empty();
        }
        final Ports.AccessTokenClaims accessClaims = new Ports.AccessTokenClaims(
            UserId.of(claims.getSubject()), Jti.of(claims.getId()), claims.getExpiration().toInstant());
        return accessTokenRevocationStore.isInvalidated(accessClaims.jti())
            ? Optional.empty()
            : Optional.of(accessClaims);
    }

    private boolean hasAudience(final Claims claims, final String expectedAudience) {
        return hasAudienceValue(claims.get("aud"), expectedAudience);
    }

    static boolean hasAudienceValue(final Object audience, final String expectedAudience) {
        if (audience instanceof String value) {
            return expectedAudience.equals(value);
        }
        if (audience instanceof Iterable<?> values) {
            for (final Object value : values) {
                if (expectedAudience.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Optional<Ports.AccessTokenClaims> extractAccessTokenClaims(final String payloadJson, final Date expiration) {
        if (payloadJson == null || payloadJson.isBlank() || expiration == null) {
            return Optional.empty();
        }
        try {
            final Map<String, Object> payload = objectMapper.readValue(payloadJson, MAP_TYPE);
            if (payload.containsKey("aud") && payload.get("aud") != null && !String.valueOf(payload.get("aud")).isBlank()) {
                return Optional.empty();
            }
            final String sub = (String) payload.get("sub");
            final String jti = (String) payload.get("jti");
            if (sub == null || sub.isBlank() || jti == null || jti.isBlank()) {
                return Optional.empty();
            }
            final Ports.AccessTokenClaims claims = new Ports.AccessTokenClaims(
                    UserId.of(sub),
                    Jti.of(jti),
                    expiration.toInstant());
            return accessTokenRevocationStore.isInvalidated(claims.jti()) ? Optional.empty() : Optional.of(claims);
        } catch (final Exception e) {
            LOGGER.log(Level.FINE, "Failed to parse access token payload", e);
            return Optional.empty();
        }
    }

}

