package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Mock implementation of TokenSigner for testing.
 * Signs tokens by prefixing them and stores them for verification.
 */
public class MockTokenSigner implements Ports.TokenSigner {
    private final Map<String, SignedToken> signedTokens = new HashMap<>();
    private long tokenCounter = 0;

    /**
     * Sign a token payload and return a signed token string.
     * For testing, returns a simple format: "mock.<counter>.<payload>"
     */
    @Override
    public String sign(final String payload, final Instant expiresAt) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("Payload cannot be null or empty");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("ExpiresAt cannot be null");
        }
        
        String token = "mock." + (++tokenCounter) + "." + payload;
        signedTokens.put(token, new SignedToken(payload, expiresAt));
        return token;
    }

    /**
     * Verify a token's signature.
     */
    @Override
    public boolean verify(final String token) {
        if (token == null || !token.startsWith("mock.")) {
            return false;
        }
        
        SignedToken signedToken = signedTokens.get(token);
        if (signedToken == null) {
            return false;
        }
        
        // Check if token has expired
        return Instant.now().isBefore(signedToken.expiresAt);
    }

    /**
     * Get the payload from a signed token.
     */
    @Override
    public String getPayload(final String token) {
        SignedToken signedToken = signedTokens.get(token);
        if (signedToken == null) {
            throw new IllegalArgumentException("Token not found or invalid");
        }
        return signedToken.payload;
    }

    /**
     * Reset all signed tokens (useful for test cleanup).
     */
    public void reset() {
        signedTokens.clear();
        tokenCounter = 0;
    }

    /**
     * Get number of tokens signed (useful for assertions in tests).
     */
    public int getSignedTokenCount() {
        return signedTokens.size();
    }

    private static class SignedToken {
        final String payload;
        final Instant expiresAt;

        SignedToken(String payload, Instant expiresAt) {
            this.payload = payload;
            this.expiresAt = expiresAt;
        }
    }
}
