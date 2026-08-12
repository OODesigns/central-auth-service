package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.UserId;
import io.github.bucket4j.Bucket;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple per-user rate limiter for 2FA verification attempts using Bucket4j.
 * Mirrors the behaviour of {@link LoginRateLimiter} but keys only on user ID.
 */
public final class TotpRateLimiter implements Ports.TotpRateLimiter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration duration;

    public TotpRateLimiter() {
        this(5, Duration.ofMinutes(1));
    }

    public TotpRateLimiter(final int maxAttempts, final Duration duration) {
        if (maxAttempts <= 0) throw new IllegalArgumentException("maxAttempts must be positive");
        if (duration == null || duration.isNegative() || duration.isZero())
            throw new IllegalArgumentException("duration must be positive");
        this.maxAttempts = maxAttempts;
        this.duration = duration;
    }

    @Override
    public Ports.RateLimitResult checkLimit(final UserId userId) {
        if (userId == null) throw new IllegalArgumentException("UserId cannot be null");

        final String key = "totp:user:" + userId.value();
        final Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket());

        if (bucket.tryConsume(1)) {
            return Ports.RateLimitResult.allowed();
        }

        return Ports.RateLimitResult.blocked("2FA verification rate limit exceeded. Try again later.");
    }

    private Bucket createBucket() {
        return Bucket.builder()
            .addLimit(limit -> limit.capacity(maxAttempts).refillGreedy(maxAttempts, duration))
            .build();
    }

    public void reset() { buckets.clear(); }
}

