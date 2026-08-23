package com.oodesigns.cas.infrastructure.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.KeyPassword;
import com.oodesigns.cas.domain.value.Payload;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Production implementation of TokenSigner using JWT (JSON Web Tokens).
 * Signs tokens using HS256 algorithm with a secret key.
 * <p>
 * Security Properties:
 * - Uses HMAC SHA-256 for token integrity verification
 * - Retrieves KeyPassword instances on-demand via KeySupplier to avoid retaining secrets
 * - Requires secure secret key (minimum 256 bits for HS256)
 * - Tokens include expiration time for time-bounded validity
 * - Payloads are base64-encoded in the token
 * - Automatically clears sensitive data via try-with-resources
 * <p>
 * Note: In production, the secret key should be:
 * - Loaded from secure configuration (environment variables, vaults, etc.) via KeySupplier implementations
 * - Provided as KeyPassword objects that can be cleared immediately after use
 * - Never hardcoded in source code
 * - Rotated regularly
 * <p>
 * Requires the jjwt library: io.jsonwebtoken:jjwt-api:0.12.1
 */
public final class JwtTokenSigner implements Ports.TokenSigner {
    private static final Logger logger = Logger.getLogger(JwtTokenSigner.class.getName());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private final KeySupplier keySupplier;
    private final String keyId;
    private final ObjectMapper objectMapper;

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
        this.objectMapper = new ObjectMapper();
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

    /**
     * Sign with password using try-with-resources for automatic cleanup.
     * The SecretKey is constructed from the password bytes and JJWT handles
     * internal byte array management. The password is automatically closed
     * by try-with-resources, clearing all sensitive data.
     *
     * @param payload Payload to embed in token
     * @param expiresAt Token expiration time
     * @param password KeyPassword containing the secret key material
     * @return Optional containing the JWT token string
     */
    private Optional<String> signWithPassword(final Payload payload, final Instant expiresAt, final KeyPassword password) {
        try (password) {
            // Convert password bytes to SecretKey for cryptographic operations
            // JJWT's Keys.hmacShaKeyFor() constructs the SecretKey from byte array
            // and manages the internal bytes securely
            final SecretKey secretKey = Keys.hmacShaKeyFor(password.toUtf8Bytes());
                final Map<String, Object> claims = objectMapper.readValue(payload.value(), MAP_TYPE);

            final String token = Jwts.builder()
                    .header().keyId(keyId).and()
                    .claims(claims)
                    .claim("ver", 2)
                    .claim("payload", payload.value())
                    .expiration(Date.from(expiresAt))
                    .signWith(secretKey, Jwts.SIG.HS256)
                    .compact();

            return Optional.of(token);
        } catch (final Exception _) {
            // Log the error for debugging/auditing (at debug level to avoid sensitive info exposure)
            // Use lambda to defer string formatting until log level is enabled
            logger.log(Level.FINE, () -> String.format("JWT signing failed for key ID: %s", keyId));
            return Optional.empty();
        }
        // password.close() automatically called here by try-with-resources,
        // clearing all password bytes from memory
    }
}

