package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

/**
 * Production implementation of TokenSigner using JWT (JSON Web Tokens).
 * Signs tokens using HS256 algorithm with a secret key.
 * 
 * Security Properties:
 * - Uses HMAC SHA-256 for token integrity verification
 * - Requires secure secret key (minimum 256 bits for HS256)
 * - Tokens include expiration time for time-bounded validity
 * - Payloads are base64-encoded in the token
 * 
 * Note: In production, the secret key should be:
 * - Loaded from secure configuration (environment variables, vaults, etc.)
 * - Never hardcoded in source code
 * - Rotated regularly
 * 
 * Requires the jjwt library: io.jsonwebtoken:jjwt-api:0.12.1
 */
public final class JwtTokenSigner implements Ports.TokenSigner {
    private final byte[] secretKey;

    /**
     * Construct a JWT token signer with the provided secret key.
     * 
     * @param secretKey The secret key for signing tokens (must be at least 256 bits/32 bytes for HS256)
     * @throws IllegalArgumentException if secret key is null or insufficient length
     */
    public JwtTokenSigner(final String secretKey) {
        Objects.requireNonNull(secretKey, "Secret key cannot be null");
        if (secretKey.length() < 32) {
            throw new IllegalArgumentException("Secret key must be at least 32 characters (256 bits) for HS256");
        }
        this.secretKey = secretKey.getBytes(StandardCharsets.UTF_8);
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
    public String sign(final String payload, final Instant expiresAt) {
        Objects.requireNonNull(payload, "Payload cannot be null");
        Objects.requireNonNull(expiresAt, "ExpiresAt cannot be null");
        
        if (payload.isBlank()) {
            throw new IllegalArgumentException("Payload cannot be empty");
        }

        try {
            // Add the JSON payload as a custom claim with the key "payload"
            // This preserves the original structure while using JWT standard features
            return Jwts.builder()
                    .claim("payload", payload)
                    .expiration(Date.from(expiresAt))
                    .signWith(Keys.hmacShaKeyFor(secretKey), Jwts.SIG.HS256)
                    .compact();
        } catch (final RuntimeException e) {
            // Wrap JWT exceptions as runtime exceptions
            throw new IllegalStateException("Failed to sign JWT token", e);
        }
    }
}
