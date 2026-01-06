package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockRateLimiterTest {

    @Test
    void tracksCallsAndBlocksAfterLimit() {
        final MockRateLimiter limiter = new MockRateLimiter(2);

        assertEquals(Ports.RateLimitResult.allowed(), limiter.checkLimit("user"));
        assertEquals(Ports.RateLimitResult.allowed(), limiter.checkLimit("user"));
        assertEquals(Ports.RateLimitResult.blocked("Rate limit exceeded for: user"), limiter.checkLimit("user"));
        assertTrue(limiter.isBlocked("user"));
        assertEquals(3, limiter.getCallCount("user"));
    }

    @Test
    void resetClearsSingleKey() {
        final MockRateLimiter limiter = new MockRateLimiter(1);
        limiter.checkLimit("key");
        limiter.checkLimit("key"); // now blocked
        assertTrue(limiter.isBlocked("key"));

        limiter.reset("key");
        assertFalse(limiter.isBlocked("key"));
        assertEquals(0, limiter.getCallCount("key"));
        assertEquals(Ports.RateLimitResult.allowed(), limiter.checkLimit("key"));
    }

    @Test
    void resetAllClearsAllState() {
        final MockRateLimiter limiter = new MockRateLimiter(1);
        limiter.checkLimit("a");
        limiter.checkLimit("a");
        limiter.checkLimit("b");

        assertTrue(limiter.isBlocked("a"));
        assertEquals(2, limiter.getCallCount("a"));
        assertEquals(1, limiter.getCallCount("b"));

        limiter.resetAll();

        assertFalse(limiter.isBlocked("a"));
        assertFalse(limiter.isBlocked("b"));
        assertEquals(0, limiter.getCallCount("a"));
        assertEquals(0, limiter.getCallCount("b"));
    }
}
