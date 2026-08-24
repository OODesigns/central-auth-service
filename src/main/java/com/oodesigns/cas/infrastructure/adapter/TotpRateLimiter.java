package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.UserId;
import io.github.bucket4j.Bucket;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.LongSupplier;

/**
 * Simple per-user rate limiter for 2FA verification attempts using Bucket4j.
 * Mirrors the behaviour of {@link LoginRateLimiter} but keys only on user ID.
 */
public final class TotpRateLimiter implements Ports.TotpRateLimiter {
    private static final int DEFAULT_MAX_TRACKED_KEYS = 100_000;
    private final Map<String, TrackedBucket> buckets = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration duration;
    private final int maxTrackedKeys;
    private final LongSupplier nanoTime;
    private final Semaphore availableSlots;

    public TotpRateLimiter() {
        this(5, Duration.ofMinutes(1));
    }

    public TotpRateLimiter(final int maxAttempts, final Duration duration) {
        this(maxAttempts, duration, DEFAULT_MAX_TRACKED_KEYS, System::nanoTime);
    }

    TotpRateLimiter(final int maxAttempts, final Duration duration,
                    final int maxTrackedKeys, final LongSupplier nanoTime) {
        if (maxAttempts <= 0) throw new IllegalArgumentException("maxAttempts must be positive");
        if (duration == null || duration.isNegative() || duration.isZero())
            throw new IllegalArgumentException("duration must be positive");
        if (maxTrackedKeys <= 0) throw new IllegalArgumentException("maxTrackedKeys must be positive");
        this.maxAttempts = maxAttempts;
        this.duration = duration;
        this.maxTrackedKeys = maxTrackedKeys;
        this.nanoTime = java.util.Objects.requireNonNull(nanoTime, "Nano time supplier cannot be null");
        this.availableSlots = new Semaphore(maxTrackedKeys);
    }

    @Override
    public Ports.RateLimitResult checkLimit(final UserId userId) {
        if (userId == null) throw new IllegalArgumentException("UserId cannot be null");

        final String key = "totp:user:" + userId.value();
        return getOrCreateBucket(key)
            .map(tracked -> tracked.bucket().tryConsume(1)
                ? Ports.RateLimitResult.allowed()
                : blocked())
            .orElseGet(this::blocked);
    }

    private java.util.Optional<TrackedBucket> getOrCreateBucket(final String key) {
        final long now = nanoTime.getAsLong();
        evictExpired(now);
        return java.util.Optional.ofNullable(buckets.compute(key, (ignored, existing) -> {
            if (existing != null) return existing.touch(expiresAt(now));
            return reserveSlot(now) ? new TrackedBucket(createBucket(), expiresAt(now)) : null;
        }));
    }

    private boolean reserveSlot(final long now) {
        if (availableSlots.availablePermits() == 0) evictExpired(now);
        return availableSlots.tryAcquire();
    }

    private void evictExpired(final long now) {
        buckets.forEach((key, tracked) -> {
            if (tracked.expiresAtNanos() <= now && buckets.remove(key, tracked)) availableSlots.release();
        });
    }

    private long expiresAt(final long now) {
        return now + duration.toNanos();
    }

    private Ports.RateLimitResult blocked() {
        return Ports.RateLimitResult.blocked("2FA verification rate limit exceeded. Try again later.");
    }

    private Bucket createBucket() {
        return Bucket.builder()
            .addLimit(limit -> limit.capacity(maxAttempts).refillGreedy(maxAttempts, duration))
            .build();
    }

    public void reset() {
        buckets.clear();
        availableSlots.drainPermits();
        availableSlots.release(maxTrackedKeys);
    }

    public int getTrackedKeyCount() {
        return buckets.size();
    }

    private record TrackedBucket(Bucket bucket, long expiresAtNanos) {
        private TrackedBucket touch(final long newExpiryNanos) {
            return new TrackedBucket(bucket, newExpiryNanos);
        }
    }
}

