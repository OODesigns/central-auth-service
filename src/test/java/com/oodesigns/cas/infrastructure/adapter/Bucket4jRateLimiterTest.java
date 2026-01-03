package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.service.Ports.RateLimitResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Bucket4jRateLimiter adapter.
 * Tests rate limiting behavior, validation, and edge cases.
 */
class Bucket4jRateLimiterTest {

    private Bucket4jRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new Bucket4jRateLimiter(3, Duration.ofMinutes(1));
    }

    private boolean isAllowed(RateLimitResult result) {
        return result.mapTo(_ -> true).orElse(_ -> false);
    }

    private String getBlockedMessage(RateLimitResult result) {
        AtomicReference<String> msg = new AtomicReference<>();
        result.mapTo(_ -> null).orElse(blocked -> {
            msg.set(blocked.message());
            return null;
        });
        return msg.get();
    }

    @Test
    void testDefaultConstructorAllowsFiveAttempts() {
        var defaultLimiter = new Bucket4jRateLimiter();
        String key = "default-test";
        
        // Should allow 5 attempts
        for (int i = 0; i < 5; i++) {
            var result = defaultLimiter.checkLimit(key);
            assertTrue(isAllowed(result), "Attempt %d should be allowed".formatted(i + 1));
        }
        
        // 6th should be blocked
        var result = defaultLimiter.checkLimit(key);
        assertFalse(isAllowed(result), "6th attempt should be blocked");
    }

    @Test
    void testCheckLimitAllowsWithinLimit() {
        String key = "test-key";
        
        // All 3 attempts should be allowed
        for (int i = 0; i < 3; i++) {
            var result = rateLimiter.checkLimit(key);
            assertTrue(isAllowed(result), "Attempt %d should be allowed".formatted(i + 1));
        }
    }

    @Test
    void testCheckLimitBlocksAfterExceedingLimit() {
        String key = "block-test";
        
        // Exhaust the limit
        for (int i = 0; i < 3; i++) {
            rateLimiter.checkLimit(key);
        }
        
        // 4th attempt should be blocked
        var result = rateLimiter.checkLimit(key);
        
        assertFalse(isAllowed(result), "Should be blocked after limit");
        String message = getBlockedMessage(result);
        assertNotNull(message, "Should have a message");
        assertTrue(message.contains("Rate limit exceeded"), 
            "Message should indicate rate limit exceeded");
    }

    @Test
    void testCheckLimitTracksKeysSeparately() {
        String key1 = "user-1";
        String key2 = "user-2";
        
        // Exhaust limit for key1
        for (int i = 0; i < 3; i++) {
            rateLimiter.checkLimit(key1);
        }
        
        // key1 should be blocked
        assertFalse(isAllowed(rateLimiter.checkLimit(key1)));
        
        // key2 should still be allowed
        assertTrue(isAllowed(rateLimiter.checkLimit(key2)));
    }

    @Test
    void testCheckLimitThrowsForNullKey() {
        assertThrows(IllegalArgumentException.class, () -> rateLimiter.checkLimit(null));
    }

    @Test
    void testCheckLimitThrowsForEmptyKey() {
        assertThrows(IllegalArgumentException.class, () -> rateLimiter.checkLimit(""));
    }

    @Test
    void testConstructorThrowsForZeroMaxAttempts() {
        Duration duration = Duration.ofMinutes(1);
        assertThrows(IllegalArgumentException.class, 
            () -> new Bucket4jRateLimiter(0, duration));
    }

    @Test
    void testConstructorThrowsForNegativeMaxAttempts() {
        Duration duration = Duration.ofMinutes(1);
        assertThrows(IllegalArgumentException.class, 
            () -> new Bucket4jRateLimiter(-1, duration));
    }

    @Test
    void testConstructorThrowsForNullDuration() {
        assertThrows(IllegalArgumentException.class, 
            () -> new Bucket4jRateLimiter(5, null));
    }

    @Test
    void testConstructorThrowsForZeroDuration() {
        Duration duration = Duration.ZERO;
        assertThrows(IllegalArgumentException.class, 
            () -> new Bucket4jRateLimiter(5, duration));
    }

    @Test
    void testConstructorThrowsForNegativeDuration() {
        Duration duration = Duration.ofMinutes(-1);
        assertThrows(IllegalArgumentException.class, 
            () -> new Bucket4jRateLimiter(5, duration));
    }

    @Test
    void testResetClearsAllBuckets() {
        String key = "reset-test";
        
        // Exhaust the limit
        for (int i = 0; i < 3; i++) {
            rateLimiter.checkLimit(key);
        }
        assertFalse(isAllowed(rateLimiter.checkLimit(key)));
        
        // Reset
        rateLimiter.reset();
        
        // Should be allowed again
        assertTrue(isAllowed(rateLimiter.checkLimit(key)));
    }

    @Test
    void testGetTrackedKeyCountReturnsCorrectCount() {
        assertEquals(0, rateLimiter.getTrackedKeyCount());
        
        rateLimiter.checkLimit("key1");
        assertEquals(1, rateLimiter.getTrackedKeyCount());
        
        rateLimiter.checkLimit("key2");
        assertEquals(2, rateLimiter.getTrackedKeyCount());
        
        rateLimiter.checkLimit("key1"); // Same key
        assertEquals(2, rateLimiter.getTrackedKeyCount());
    }

    @Test
    void testResetClearsTrackedKeys() {
        rateLimiter.checkLimit("key1");
        rateLimiter.checkLimit("key2");
        assertEquals(2, rateLimiter.getTrackedKeyCount());
        
        rateLimiter.reset();
        
        assertEquals(0, rateLimiter.getTrackedKeyCount());
    }

    @Test
    void testImplementsRateLimiterPort() {
        assertInstanceOf(Ports.RateLimiter.class, rateLimiter);
    }
}
