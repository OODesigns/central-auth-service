package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.application.command.LoginCommand;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.IpAddress;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.Username;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockRateLimiterTest {

    private LoginCommand createCommand(final String username, final String ipAddress) {
        return new LoginCommand(
            Username.of(username),
            Password.of("ValidPassword123".toCharArray()),
            IpAddress.of(ipAddress)
        );
    }

    @Test
    void tracksCallsAndBlocksAfterLimit() {
        final MockRateLimiter limiter = new MockRateLimiter(2);
        final LoginCommand cmd = createCommand("user", "192.168.1.1");

        assertEquals(Ports.RateLimitResult.allowed(), limiter.checkLimit(cmd));
        assertEquals(Ports.RateLimitResult.allowed(), limiter.checkLimit(cmd));
        final Ports.RateLimitResult blocked = limiter.checkLimit(cmd);
        assertInstanceOf(Ports.RateLimitResult.Blocked.class, blocked);
    }

    @Test
    void resetClearsSingleKey() {
        final MockRateLimiter limiter = new MockRateLimiter(1);
        final LoginCommand cmd = createCommand("user", "192.168.1.1");

        // First attempt is allowed
        assertEquals(Ports.RateLimitResult.allowed(), limiter.checkLimit(cmd));

        // Second attempt on any of the 3 buckets will be blocked
        final Ports.RateLimitResult result = limiter.checkLimit(cmd);
        assertInstanceOf(Ports.RateLimitResult.Blocked.class, result);

        // Reset all buckets
        limiter.resetAll();

        // Now should be allowed again
        assertEquals(Ports.RateLimitResult.allowed(), limiter.checkLimit(cmd));
    }

    @Test
    void resetAllClearsAllState() {
        final MockRateLimiter limiter = new MockRateLimiter(1);
        final LoginCommand cmd1 = createCommand("user1", "192.168.1.1");
        final LoginCommand cmd2 = createCommand("user2", "192.168.1.2");

        limiter.checkLimit(cmd1);
        limiter.checkLimit(cmd1);
        limiter.checkLimit(cmd2);

        limiter.resetAll();

        assertEquals(Ports.RateLimitResult.allowed(), limiter.checkLimit(cmd1));
        assertEquals(Ports.RateLimitResult.allowed(), limiter.checkLimit(cmd2));
    }
}
