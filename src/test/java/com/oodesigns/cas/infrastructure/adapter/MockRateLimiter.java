package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.application.command.LoginCommand;
import com.oodesigns.cas.domain.service.Ports;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock implementation of RateLimiter for testing.
 * Tracks call counts for multi-key rate limiting (IP, username, combined).
 */
public class MockRateLimiter implements Ports.RateLimiter {
    private final Map<String, AtomicInteger> callCounts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Set<String> blockedKeys = ConcurrentHashMap.newKeySet();

    public MockRateLimiter(final int maxAttempts) {
        this.maxAttempts = maxAttempts;
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

    private Ports.RateLimitResult checkLimitForKey(final String key) {
        if (blockedKeys.contains(key)) {
            return Ports.RateLimitResult.blocked("Rate limit exceeded");
        }

        final int currentCount = callCounts.computeIfAbsent(key, ignored -> new AtomicInteger(0)).incrementAndGet();

        if (currentCount > maxAttempts) {
            blockedKeys.add(key);
            return Ports.RateLimitResult.blocked("Rate limit exceeded");
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


