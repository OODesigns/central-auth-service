package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.application.command.LoginCommand;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.service.Ports.RateLimitResult;
import com.oodesigns.cas.domain.value.IpAddress;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.Username;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LoginRateLimiter adapter.
 * Tests multi-key rate limiting behavior (IP, username, combined), validation, and edge cases.
 */
class LoginRateLimiterTest {

    private LoginRateLimiter rateLimiter;
    private LoginCommand command1;
    private LoginCommand command2;

    @BeforeEach
    void setUp() {
        rateLimiter = new LoginRateLimiter(3, Duration.ofMinutes(1));
        command1 = new LoginCommand(
            Username.of("john_doe"),
            Password.of("ValidPassword123".toCharArray()),
            IpAddress.of("192.168.1.1")
        );
        command2 = new LoginCommand(
            Username.of("jane_doe"),
            Password.of("ValidPassword123".toCharArray()),
            IpAddress.of("192.168.1.2")
        );
    }

    private boolean isAllowed(final RateLimitResult result) {
        return result.mapTo(_ -> true).orElse(_ -> false);
    }

    private String getBlockedMessage(final RateLimitResult result) {
        final AtomicReference<String> msg = new AtomicReference<>();
        result.mapTo(_ -> null).orElse(blocked -> {
            msg.set(blocked.message());
            return null;
        });
        return msg.get();
    }

    @Test
    void testCheckLimitAllowsWithinLimit() {
        // All 3 attempts should be allowed
        for (int i = 0; i < 3; i++) {
            final var result = rateLimiter.checkLimit(command1);
            assertTrue(isAllowed(result), "Attempt %d should be allowed".formatted(i + 1));
        }
    }

    @Test
    void testCheckLimitBlocksAfterExceedingLimit() {
        // Exhaust the limit (3 attempts on any of the 3 buckets will eventually block)
        for (int i = 0; i < 3; i++) {
            rateLimiter.checkLimit(command1);
        }

        // 4th attempt should be blocked
        final var result = rateLimiter.checkLimit(command1);

        assertFalse(isAllowed(result), "Should be blocked after limit");
        final String message = getBlockedMessage(result);
        assertNotNull(message, "Should have a message");
        assertTrue(message.contains("Rate limit exceeded"),
            "Message should indicate rate limit exceeded");
    }

    @Test
    void testCheckLimitTracksDifferentUsersIndependently() {
        // Exhaust limit for command1 (john_doe from 192.168.1.1)
        for (int i = 0; i < 3; i++) {
            rateLimiter.checkLimit(command1);
        }

        // command1 should be blocked
        assertFalse(isAllowed(rateLimiter.checkLimit(command1)));

        // command2 (jane_doe from 192.168.1.2) should still be allowed
        assertTrue(isAllowed(rateLimiter.checkLimit(command2)));
    }

    @Test
    void testCheckLimitThrowsForNullCommand() {
        assertThrows(IllegalArgumentException.class, () -> rateLimiter.checkLimit(null));
    }

    @Test
    void testConstructorThrowsForZeroMaxAttempts() {
        final Duration duration = Duration.ofMinutes(1);
        assertThrows(IllegalArgumentException.class,
            () -> new LoginRateLimiter(0, duration));
    }

    @Test
    void testConstructorThrowsForNegativeMaxAttempts() {
        final Duration duration = Duration.ofMinutes(1);
        assertThrows(IllegalArgumentException.class,
            () -> new LoginRateLimiter(-1, duration));
    }

    @Test
    void testConstructorThrowsForNullDuration() {
        assertThrows(IllegalArgumentException.class,
            () -> new LoginRateLimiter(5, null));
    }

    @Test
    void testConstructorThrowsForZeroDuration() {
        final Duration duration = Duration.ZERO;
        assertThrows(IllegalArgumentException.class,
            () -> new LoginRateLimiter(5, duration));
    }

    @Test
    void testConstructorThrowsForNegativeDuration() {
        final Duration duration = Duration.ofMinutes(-1);
        assertThrows(IllegalArgumentException.class,
            () -> new LoginRateLimiter(5, duration));
    }

    @Test
    void testResetClearsAllBuckets() {
        // Exhaust the limit
        for (int i = 0; i < 3; i++) {
            rateLimiter.checkLimit(command1);
        }
        assertFalse(isAllowed(rateLimiter.checkLimit(command1)));

        // Reset
        rateLimiter.reset();

        // Should be allowed again
        assertTrue(isAllowed(rateLimiter.checkLimit(command1)));
    }

    @Test
    void testGetTrackedKeyCountReturnsCorrectCount() {
        assertEquals(0, rateLimiter.getTrackedKeyCount());

        rateLimiter.checkLimit(command1);
        // command1 creates 3 buckets (IP, username, combined)
        assertEquals(3, rateLimiter.getTrackedKeyCount());

        rateLimiter.checkLimit(command2);
        // command2 creates 3 more buckets (different IP and username)
        assertEquals(6, rateLimiter.getTrackedKeyCount());
    }

    @Test
    void testResetClearsTrackedKeys() {
        rateLimiter.checkLimit(command1);
        rateLimiter.checkLimit(command2);
        assertTrue(rateLimiter.getTrackedKeyCount() > 0);

        rateLimiter.reset();

        assertEquals(0, rateLimiter.getTrackedKeyCount());
    }

    @Test
    void testImplementsRateLimiterPort() {
        assertInstanceOf(Ports.RateLimiter.class, rateLimiter);
    }

    @Test
    void testIpBucketBlocksEarly() {
        final LoginRateLimiter limiter = new LoginRateLimiter(1, java.time.Duration.ofMinutes(1));
        final var cmdA = new LoginCommand(Username.of("a12"), Password.of("ValidPassword12345".toCharArray()), IpAddress.of("10.0.0.1"));
        final var cmdB = new LoginCommand(Username.of("b12"), Password.of("ValidPassword12345".toCharArray()), IpAddress.of("10.0.0.1"));

        // First attempt consumes IP bucket
        assertTrue(limiter.checkLimit(cmdA).mapTo(_ -> true).orElse(b -> false));
        // Second attempt from different username but same IP should be blocked at IP level
        assertFalse(limiter.checkLimit(cmdB).mapTo(_ -> true).orElse(b -> false));
    }

    @Test
    void testUsernameBucketBlocksEarly() {
        final LoginRateLimiter limiter = new LoginRateLimiter(1, java.time.Duration.ofMinutes(1));
        final var cmdA = new LoginCommand(Username.of("same"), Password.of("ValidPassword12345".toCharArray()), IpAddress.of("10.0.0.1"));
        final var cmdB = new LoginCommand(Username.of("same"), Password.of("ValidPassword12345".toCharArray()), IpAddress.of("10.0.0.2"));

        // First attempt consumes username bucket
        assertTrue(limiter.checkLimit(cmdA).mapTo(_ -> true).orElse(b -> false));
        // Second attempt from different IP but same username should be blocked at username level
        assertFalse(limiter.checkLimit(cmdB).mapTo(_ -> true).orElse(b -> false));
    }

    @Test
    void testCheckLimitForKeyRejectsEmptyKeyViaReflection() throws Exception {
        final java.lang.reflect.Method m = LoginRateLimiter.class.getDeclaredMethod("checkLimitForKey", String.class);
        m.setAccessible(true);
        final LoginRateLimiter limiter = new LoginRateLimiter();
        assertThrows(IllegalArgumentException.class, () -> {
            try {
                m.invoke(limiter, "");
            } catch (final java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
}


