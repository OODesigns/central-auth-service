package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter implementation using Bucket4j.
 * Provides per-key rate limiting with configurable limits.
 * Uses in-memory bucket storage suitable for integration testing.
 * For production single-instance deployments, use this directly or extend with cache-based storage (Redis, etc).
 */
public class Bucket4jRateLimiter implements Ports.RateLimiter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Bandwidth bandwidth;

    /**
     * Create a rate limiter with default limits (5 attempts per minute per key).
     */
    public Bucket4jRateLimiter() {
        this(5, Duration.ofMinutes(1));
    }

    /**
     * Create a rate limiter with custom limits.
     * @param maxAttempts maximum number of attempts allowed
     * @param duration time window for the limit
     */
    public Bucket4jRateLimiter(final int maxAttempts, final Duration duration) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        this.bandwidth = Bandwidth.classic(maxAttempts, Refill.intervally(maxAttempts, duration));
    }

    @Override
    public Optional<String> checkLimit(final String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        @SuppressWarnings("deprecation")
        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket4j.builder()
            .addLimit(bandwidth)
            .build());

        if (bucket.tryConsume(1)) {
            return Optional.empty();
        }

        String errorMsg = String.format(
            "Rate limit exceeded for '%s'. Try again later.",
            key
        );
        return Optional.of(errorMsg);
    }

    /**
     * Clear all stored buckets (useful for testing or resetting state).
     */
    public void reset() {
        buckets.clear();
    }

    /**
     * Get number of tracked keys (useful for testing).
     */
    public int getTrackedKeyCount() {
        return buckets.size();
    }
}
