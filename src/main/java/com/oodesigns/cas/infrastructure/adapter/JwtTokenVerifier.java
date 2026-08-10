package com.oodesigns.cas.infrastructure.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.KeyPassword;
import com.oodesigns.cas.domain.value.UserId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private static final String EXPECTED_AUDIENCE = "2fa_verification";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final KeySupplier keySupplier;
    private final String keyId;
    private final ObjectMapper objectMapper;

    /**
     * @param keySupplier supplies the signing key (same key used by {@link JwtTokenSigner})
     * @param keyId       environment variable / key identifier for the signing key
     */
    public JwtTokenVerifier(final KeySupplier keySupplier, final String keyId) {
        this.keySupplier = Objects.requireNonNull(keySupplier, "KeySupplier cannot be null");
        this.keyId = Objects.requireNonNull(keyId, "Key ID cannot be null");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * @param keySupplier  supplies the signing key
     * @param keyId        key identifier
     * @param objectMapper Jackson mapper for parsing the nested payload JSON
     */
    JwtTokenVerifier(final KeySupplier keySupplier, final String keyId, final ObjectMapper objectMapper) {
        this.keySupplier = Objects.requireNonNull(keySupplier, "KeySupplier cannot be null");
        this.keyId = Objects.requireNonNull(keyId, "Key ID cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns {@link Optional#empty()} when the token is {@code null}, blank, expired,
     * has an invalid signature, has the wrong audience, or cannot be parsed.
     */
    @Override
    public Optional<UserId> verify2FAVerificationToken(final String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return keySupplier.getPassword(keyId)
                .flatMap(password -> parseAndVerify(token, password));
    }

    private Optional<UserId> parseAndVerify(final String token, final KeyPassword password) {
        try (password) {
            final SecretKey key = Keys.hmacShaKeyFor(password.toUtf8Bytes());
            final Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            final String payloadJson = claims.get("payload", String.class);
            return extractUserId(payloadJson);
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, () -> "Failed to verify 2FA token: " + e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<UserId> extractUserId(final String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Optional.empty();
        }
        try {
            final Map<String, Object> payload = objectMapper.readValue(payloadJson, MAP_TYPE);
            final String aud = (String) payload.get("aud");
            if (!EXPECTED_AUDIENCE.equals(aud)) {
                return Optional.empty();
            }
            final String sub = (String) payload.get("sub");
            if (sub == null || sub.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(UserId.of(sub));
        } catch (final Exception e) {
            LOGGER.log(Level.FINE, () -> "Failed to parse 2FA token payload: " + e.getMessage());
            return Optional.empty();
        }
    }
}

