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
    public void checkLimit(String key) throws Ports.RateLimitExceededException {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        if (blockedKeys.contains(key)) {
            throw new Ports.RateLimitExceededException("Rate limit exceeded for: " + key);
        }

        AtomicInteger count = callCounts.computeIfAbsent(key, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();

        if (currentCount > maxAttempts) {
            blockedKeys.add(key);
            throw new Ports.RateLimitExceededException("Rate limit exceeded for: " + key);
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
