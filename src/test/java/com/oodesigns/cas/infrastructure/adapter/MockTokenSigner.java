package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Payload;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock implementation of TokenSigner for testing.
 * Signs tokens by prefixing them with a counter for test verification.
 */
public class MockTokenSigner implements Ports.TokenSigner {
    private final AtomicLong tokenCounter = new AtomicLong(0);

    /**
     * Sign a token payload and return a signed token string.
     * For testing, returns a simple format: "mock.<counter>.<payload>"
     */
    @Override
    public java.util.Optional<String> sign(final Payload payload, final Instant expiresAt) {
        if (payload == null) {
            return java.util.Optional.empty();
        }
        if (expiresAt == null) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of("mock." + tokenCounter.incrementAndGet() + "." + payload.value());
    }

    /**
     * Reset token counter (useful for test cleanup).
     */
    public void reset() {
        tokenCounter.set(0);
    }

    /**
     * Get number of tokens signed (useful for assertions in tests).
     */
    public long getSignedTokenCount() {
        return tokenCounter.get();
    }
}
