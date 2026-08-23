package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.application.command.LoginCommand;
import com.oodesigns.cas.domain.service.Ports;
import io.github.bucket4j.Bucket;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.LongSupplier;

/**
 * Rate limiter implementation using Bucket4j.
 * Provides multi-key rate limiting for login attempts with three buckets:
 * - IP address rate limit
 * - Username rate limit
 * - Combined IP + username rate limit
 * State is bounded and expires after inactivity. New keys fail closed at capacity;
 * clustered deployments should replace this adapter with a shared distributed store.
 */
public class LoginRateLimiter implements Ports.RateLimiter {
    private static final int DEFAULT_MAX_TRACKED_KEYS = 100_000;

    private final Map<String, TrackedBucket> buckets = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration duration;
    private final int maxTrackedKeys;
    private final LongSupplier nanoTime;
    private final Semaphore availableSlots;

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
        this(maxAttempts, duration, DEFAULT_MAX_TRACKED_KEYS, System::nanoTime);
    }

    LoginRateLimiter(final int maxAttempts,
                     final Duration duration,
                     final int maxTrackedKeys,
                     final LongSupplier nanoTime) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        if (maxTrackedKeys <= 0) {
            throw new IllegalArgumentException("maxTrackedKeys must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.duration = duration;
        this.maxTrackedKeys = maxTrackedKeys;
        this.nanoTime = Objects.requireNonNull(nanoTime, "Nano time supplier cannot be null");
        this.availableSlots = new Semaphore(maxTrackedKeys);
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

        return checkLimitForKey(ipKey)
            .mapTo(allowedIp -> checkLimitForKey(idKey)
                .mapTo(allowedId -> checkLimitForKey(comboKey))
                .orElse(blocked -> blocked))
            .orElse(blocked -> blocked);
    }

    /**
     * Check rate limit for a single key.
     *
     * @param key the rate limit key
     * @return allowed if under limit, blocked if limit exceeded
     */
    private Ports.RateLimitResult checkLimitForKey(final String key) {

        return getOrCreateBucket(key)
            .map(bucket -> bucket.tryConsume(1)
                ? Ports.RateLimitResult.allowed()
                : blocked())
            .orElseGet(this::blocked);
    }

    private Optional<Bucket> getOrCreateBucket(final String key) {
        final long now = nanoTime.getAsLong();
        return Optional.ofNullable(buckets.compute(key, (ignored, existing) -> {
            if (existing != null) {
                return existing.expiresAtNanos() > now
                    ? existing.touch(expiresAt(now))
                    : new TrackedBucket(createBucket(), expiresAt(now));
            }
            return reserveSlot(now)
                ? new TrackedBucket(createBucket(), expiresAt(now))
                : null;
        })).map(tracked -> tracked.bucket());
    }

    private boolean reserveSlot(final long now) {
        if (availableSlots.availablePermits() == 0) {
            evictExpired(now);
        }
        return availableSlots.tryAcquire();
    }

    private void evictExpired(final long now) {
        buckets.forEach((key, tracked) -> {
            if (tracked.expiresAtNanos() <= now && buckets.remove(key, tracked)) {
                availableSlots.release();
            }
        });
    }

    private long expiresAt(final long now) {
        return now + duration.toNanos();
    }

    private Ports.RateLimitResult blocked() {
        return Ports.RateLimitResult.blocked("Rate limit exceeded. Try again later.");
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
        availableSlots.drainPermits();
        availableSlots.release(maxTrackedKeys);
    }

    /**
     * Get number of tracked keys (useful for testing).
     */
    public int getTrackedKeyCount() {
        return buckets.size();
    }

    private record TrackedBucket(Bucket bucket, long expiresAtNanos) {
        private TrackedBucket touch(final long newExpiryNanos) {
            return new TrackedBucket(bucket, newExpiryNanos);
        }
    }
}
