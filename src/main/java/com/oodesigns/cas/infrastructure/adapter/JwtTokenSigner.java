package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.KeyPassword;
import com.oodesigns.cas.domain.value.Payload;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

/**
 * Production implementation of TokenSigner using JWT (JSON Web Tokens).
 * Signs tokens using HS256 algorithm with a secret key.
 * 
 * Security Properties:
 * - Uses HMAC SHA-256 for token integrity verification
 * - Retrieves KeyPassword instances on-demand via KeySupplier to avoid retaining secrets
 * - Requires secure secret key (minimum 256 bits for HS256)
 * - Tokens include expiration time for time-bounded validity
 * - Payloads are base64-encoded in the token
 * 
 * Note: In production, the secret key should be:
 * - Loaded from secure configuration (environment variables, vaults, etc.) via KeySupplier implementations
 * - Provided as KeyPassword objects that can be cleared immediately after use
 * - Never hardcoded in source code
 * - Rotated regularly
 * 
 * Requires the jjwt library: io.jsonwebtoken:jjwt-api:0.12.1
 */
public final class JwtTokenSigner implements Ports.TokenSigner {
    private final KeySupplier keySupplier;
    private final String keyId;

    /**
     * Construct a JWT token signer that fetches passwords on-demand.
     *
     * @param keySupplier Provider that retrieves passwords per signing request
     * @param keyId Identifier for the key to retrieve from the supplier
     * @throws NullPointerException if keySupplier or keyId is null
     */
    public JwtTokenSigner(final KeySupplier keySupplier, final String keyId) {
        this.keySupplier = Objects.requireNonNull(keySupplier, "Key supplier cannot be null");
        this.keyId = Objects.requireNonNull(keyId, "Key ID cannot be null");
    }

    /**
     * Sign a JSON payload and return a JWT token.
     * The payload should be a JSON string that will be embedded in the JWT.
     * 
     * @param payload The JSON payload to sign (non-null, non-empty)
     * @param expiresAt The expiration time for the token
     * @return Optional containing JWT token string when signing succeeds
     */
    @Override
    public Optional<String> sign(final Payload payload, final Instant expiresAt) {
        if (payload == null || expiresAt == null) {
            return Optional.empty();
        }
        return retrievePassword()
                .flatMap(password -> signWithPassword(payload, expiresAt, password));
    }

    private Optional<KeyPassword> retrievePassword() {
        return keySupplier.getPassword(keyId);
    }

    private Optional<String> signWithPassword(final Payload payload, final Instant expiresAt, final KeyPassword password) {
        final byte[] secretKey = password.toUtf8Bytes();
        try {
            final String token = Jwts.builder()
                    .claim("payload", payload.toString())
                    .expiration(Date.from(expiresAt))
                    .signWith(Keys.hmacShaKeyFor(secretKey), Jwts.SIG.HS256)
                    .compact();
            return Optional.of(token);
        } catch (final RuntimeException _) {
            return Optional.empty();
        } finally {
            Arrays.fill(secretKey, (byte) 0);
            password.clear();
        }
    }
}
