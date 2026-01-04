package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import io.github.bucket4j.Bucket;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter implementation using Bucket4j.
 * Provides per-key rate limiting with configurable limits.
 */
public class Bucket4jRateLimiter implements Ports.RateLimiter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration duration;

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
        this.maxAttempts = maxAttempts;
        this.duration = duration;
    }

    @Override
    public Ports.RateLimitResult checkLimit(final String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        Bucket bucket = buckets.computeIfAbsent(key,  k-> createBucket());

        if (bucket.tryConsume(1)) {
            return Ports.RateLimitResult.allowed();
        }

        return Ports.RateLimitResult.blocked(
            String.format("Rate limit exceeded for '%s'. Try again later.", key)
        );
    }

    /**
     * Create a new bucket with the configured limits.
     */
    private Bucket createBucket() {
        return Bucket.builder()
            .addLimit(limit -> limit.capacity(maxAttempts).refillGreedy(maxAttempts, duration))
            .build();
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
