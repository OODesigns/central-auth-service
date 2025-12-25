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

    /**
     * Construct a JWT token signer that fetches passwords on-demand.
     *
     * @param keySupplier Provider that retrieves passwords per signing request
     * @throws NullPointerException if keySupplier is null
     */
    public JwtTokenSigner(final KeySupplier keySupplier) {
        this.keySupplier = Objects.requireNonNull(keySupplier, "Key supplier cannot be null");
    }

    /**
     * Sign a JSON payload and return a JWT token.
     * The payload should be a JSON string that will be embedded in the JWT.
     * 
     * @param payload The JSON payload to sign (non-null, non-empty)
     * @param expiresAt The expiration time for the token
     * @return A JWT token string that can be used for authentication
     * @throws IllegalArgumentException if payload or expiresAt is null
     */
    @Override
    public String sign(final Payload payload, final Instant expiresAt) {
        Objects.requireNonNull(payload, "Payload cannot be null");
        Objects.requireNonNull(expiresAt, "ExpiresAt cannot be null");

        try {
            final KeyPassword password = Objects.requireNonNull(keySupplier.getPassword(),
                    "Key supplier provided null password");
            final byte[] secretKey = password.toUtf8Bytes();
            try {
                // Add the JSON payload as a custom claim with the key "payload"
                // This preserves the original structure while using JWT standard features
                return Jwts.builder()
                        .claim("payload", payload.value())
                        .expiration(Date.from(expiresAt))
                        .signWith(Keys.hmacShaKeyFor(secretKey), Jwts.SIG.HS256)
                        .compact();
            } finally {
                Arrays.fill(secretKey, (byte) 0);
                password.clear();
            }
        } catch (final RuntimeException e) {
            // Wrap JWT exceptions as runtime exceptions
            throw new IllegalStateException("Failed to sign JWT token", e);
        }
    }
}
