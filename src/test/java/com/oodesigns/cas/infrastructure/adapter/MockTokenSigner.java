package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Payload;
import com.oodesigns.cas.domain.value.AccessToken;
import com.oodesigns.cas.domain.value.RefreshToken;
import com.oodesigns.cas.domain.value.TwoFactorVerificationToken;
import com.oodesigns.cas.domain.value.MfaEnrollmentToken;
import com.oodesigns.cas.domain.value.RecoveryToken;

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
    private java.util.Optional<String> sign(final Payload payload, final Instant expiresAt) {
        if (payload == null) {
            return java.util.Optional.empty();
        }
        if (expiresAt == null) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of("mock.%d.%s".formatted(tokenCounter.incrementAndGet(), payload.value()));
    }

    @Override
    public java.util.Optional<AccessToken> signAccessToken(final Payload payload, final Instant expiresAt) {
        return sign(payload, expiresAt).map(AccessToken::of);
    }

    @Override
    public java.util.Optional<RefreshToken> signRefreshToken(final Payload payload, final Instant expiresAt) {
        return sign(payload, expiresAt).map(RefreshToken::of);
    }

    @Override
    public java.util.Optional<TwoFactorVerificationToken> signTwoFactorVerificationToken(
            final Payload payload, final Instant expiresAt) {
        return sign(payload, expiresAt).map(TwoFactorVerificationToken::of);
    }

    @Override
    public java.util.Optional<MfaEnrollmentToken> signMfaEnrollmentToken(
            final Payload payload, final Instant expiresAt) {
        return sign(payload, expiresAt).map(MfaEnrollmentToken::of);
    }

    @Override
    public java.util.Optional<RecoveryToken> signRecoveryToken(
            final Payload payload, final Instant expiresAt) {
        return sign(payload, expiresAt).map(RecoveryToken::of);
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
