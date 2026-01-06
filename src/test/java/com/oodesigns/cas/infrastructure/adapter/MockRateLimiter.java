package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock implementation of RateLimiter for testing.
 * Tracks call counts per key and allows configuration of limits.
 */
public class MockRateLimiter implements Ports.RateLimiter {
    private final Map<String, AtomicInteger> callCounts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Set<String> blockedKeys = ConcurrentHashMap.newKeySet();

    public MockRateLimiter(final int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    @Override
    public Ports.RateLimitResult checkLimit(final String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        if (blockedKeys.contains(key)) {
            return Ports.RateLimitResult.blocked("Rate limit exceeded for: %s".formatted(key));
        }

        final int currentCount = callCounts.computeIfAbsent(key, ignored -> new AtomicInteger(0)).incrementAndGet();

        if (currentCount > maxAttempts) {
            blockedKeys.add(key);
            return Ports.RateLimitResult.blocked("Rate limit exceeded for: %s".formatted(key));
        }

        return Ports.RateLimitResult.allowed();
    }

    public int getCallCount(final String key) {
        final AtomicInteger count = callCounts.get(key);
        return count == null ? 0 : count.get();
    }

    public void reset(final String key) {
        callCounts.remove(key);
        blockedKeys.remove(key);
    }

    public void resetAll() {
        callCounts.clear();
        blockedKeys.clear();
    }

    public boolean isBlocked(final String key) {
        return blockedKeys.contains(key);
    }
}
