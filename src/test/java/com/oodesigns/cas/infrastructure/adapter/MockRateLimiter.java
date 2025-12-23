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

    public MockRateLimiter() {
        this(3); // Default: 3 attempts per key
    }

    public MockRateLimiter(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    @Override
    public Ports.RateLimitResult checkLimit(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        if (blockedKeys.contains(key)) {
            return new RateLimitResultImpl(false, Optional.of("Rate limit exceeded for: " + key));
        }

        int currentCount = callCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

        if (currentCount > maxAttempts) {
            blockedKeys.add(key);
            return new RateLimitResultImpl(false, Optional.of("Rate limit exceeded for: " + key));
        }

        return new RateLimitResultImpl(true, Optional.empty());
    }

    /**
     * Implementation of RateLimitResult for testing.
     */
    private static class RateLimitResultImpl implements Ports.RateLimitResult {
        private final boolean allowed;
        private final Optional<String> errorMessage;

        RateLimitResultImpl(final boolean allowed, final Optional<String> errorMessage) {
            this.allowed = allowed;
            this.errorMessage = errorMessage;
        }

        @Override
        public boolean isAllowed() {
            return allowed;
        }

        @Override
        public Optional<String> getErrorMessage() {
            return errorMessage;
        }
    }

    public int getCallCount(String key) {
        AtomicInteger count = callCounts.get(key);
        return count == null ? 0 : count.get();
    }

    public void reset(String key) {
        callCounts.remove(key);
        blockedKeys.remove(key);
    }

    public void resetAll() {
        callCounts.clear();
        blockedKeys.clear();
    }

    public boolean isBlocked(String key) {
        return blockedKeys.contains(key);
    }
}
