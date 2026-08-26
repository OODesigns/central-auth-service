package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TotpRateLimiterTest {

    private TotpRateLimiter limiter;
    private UserId userId;

    @BeforeEach
    void setUp() {
        limiter = new TotpRateLimiter(3, Duration.ofMinutes(1));
        userId = UserId.of(UUID.randomUUID());
    }

    @Test
    void allowsWithinLimit() {
        for (int i = 0; i < 3; i++) {
            final var result = limiter.checkLimit(userId);
            assertTrue(result.mapTo(_ -> true).orElse(b -> false));
        }
    }

    @Test
    void blocksAfterExceedingLimit() {
        for (int i = 0; i < 3; i++) limiter.checkLimit(userId);
        final var result = limiter.checkLimit(userId);
        assertFalse(result.mapTo(_ -> true).orElse(b -> false));
        result.mapTo(_ -> fail("Expected blocked")).orElse(blocked -> {
            assertTrue(blocked.message().contains("2FA verification rate limit"));
            return null;
        });
    }

    @Test
    void defaultConstructorAllows() {
        final TotpRateLimiter defaultLimiter = new TotpRateLimiter();
        final var result = defaultLimiter.checkLimit(userId);
        assertTrue(result.mapTo(_ -> true).orElse(b -> false));
    }

    @Test
    void constructorValidation() {
        final Duration duration = Duration.ofMinutes(1);
        assertThrows(IllegalArgumentException.class, () -> new TotpRateLimiter(0, duration));
        assertThrows(IllegalArgumentException.class, () -> new TotpRateLimiter(-1, duration));
        assertThrows(IllegalArgumentException.class, () -> new TotpRateLimiter(5, null));
        assertThrows(IllegalArgumentException.class, () -> new TotpRateLimiter(5, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
            () -> new TotpRateLimiter(5, duration, 0, System::nanoTime));
        assertThrows(NullPointerException.class,
            () -> new TotpRateLimiter(5, duration, 1, null));
    }

    @Test
    void checkLimitThrowsForNullUserId() {
        assertThrows(IllegalArgumentException.class, () -> limiter.checkLimit(null));
    }

    @Test
    void resetAllowsAfterBlocking() {
        for (int i = 0; i < 3; i++) limiter.checkLimit(userId);
        assertFalse(limiter.checkLimit(userId).mapTo(_ -> true).orElse(b -> false));
        limiter.reset();
        assertTrue(limiter.checkLimit(userId).mapTo(_ -> true).orElse(b -> false));
        assertEquals(1, limiter.getTrackedKeyCount());
    }

    @Test
    void implementsPort() {
        assertInstanceOf(Ports.TotpRateLimiter.class, limiter);
    }

    @Test
    void boundedLimiterBlocksWhenSlotsAreExhaustedAndEvictsExpiredEntries() {
        final long[] now = {0L};
        final TotpRateLimiter bounded = new TotpRateLimiter(1, Duration.ofNanos(10), 1, () -> now[0]);
        final UserId otherUser = UserId.of(UUID.randomUUID());
        assertTrue(bounded.checkLimit(userId).mapTo(_ -> true).orElse(_ -> false));
        assertFalse(bounded.checkLimit(otherUser).mapTo(_ -> true).orElse(_ -> false));
        now[0] = 11L;
        assertTrue(bounded.checkLimit(otherUser).mapTo(_ -> true).orElse(_ -> false));
    }
}

