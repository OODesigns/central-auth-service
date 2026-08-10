package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.application.command.LoginCommand;
import com.oodesigns.cas.domain.service.Ports;
import io.github.bucket4j.Bucket;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter implementation using Bucket4j.
 * Provides multi-key rate limiting for login attempts with three buckets:
 * - IP address rate limit
 * - Username rate limit
 * - Combined IP + username rate limit
 */
public class LoginRateLimiter implements Ports.RateLimiter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration duration;

    /**
     * Create a rate limiter with default limits (5 attempts per minute per key).
     */
    public LoginRateLimiter() {
        this(5, Duration.ofMinutes(1));
    }

    /**
     * Create a rate limiter with custom limits.
     * @param maxAttempts maximum number of attempts allowed
     * @param duration time window for the limit
     */
    public LoginRateLimiter(final int maxAttempts, final Duration duration) {
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
    public Ports.RateLimitResult checkLimit(final LoginCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("LoginCommand cannot be null");
        }

        // Check three rate limit buckets: IP, username, and combined
        final String ipKey = "login:ip:" + command.ipAddress().value();
        final String idKey = "login:id:" + command.username().value();
        final String comboKey = "login:ip+id:" + command.ipAddress().value() + ":" + command.username().value();

        // Check IP limit
        final Ports.RateLimitResult ipLimit = checkLimitForKey(ipKey);
        if (ipLimit instanceof Ports.RateLimitResult.Blocked) {
            return ipLimit;
        }

        // Check username limit
        final Ports.RateLimitResult idLimit = checkLimitForKey(idKey);
        if (idLimit instanceof Ports.RateLimitResult.Blocked) {
            return idLimit;
        }

        // Check combined limit
        return checkLimitForKey(comboKey);
    }

    /**
     * Check rate limit for a single key.
     *
     * @param key the rate limit key
     * @return allowed if under limit, blocked if limit exceeded
     */
    private Ports.RateLimitResult checkLimitForKey(final String key) {

        final Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket());

        if (bucket.tryConsume(1)) {
            return Ports.RateLimitResult.allowed();
        }

        return Ports.RateLimitResult.blocked(
            String.format("Rate limit exceeded. Try again later.")
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
