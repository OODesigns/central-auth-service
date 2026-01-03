package com.oodesigns.cas.domain.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortsTest {

    @Test
    void testPortsInstantiation() {
        Ports ports = new Ports();
        assertNotNull(ports);
    }

    @Test
    void blockedRecordWithValidMessageSucceeds() {
        var blocked = new Ports.RateLimitResult.Blocked("Too many attempts");
        assertEquals("Too many attempts", blocked.message());
    }

    @Test
    void blockedRecordWithNullMessageThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> 
            new Ports.RateLimitResult.Blocked(null)
        );
    }

    @Test
    void blockedRecordWithBlankMessageThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> 
            new Ports.RateLimitResult.Blocked("   ")
        );
    }

    @Test
    void allowedStaticMethodReturnsNewInstance() {
        var allowed = Ports.RateLimitResult.allowed();
        assertNotNull(allowed);
        assertTrue(allowed instanceof Ports.RateLimitResult.Allowed);
    }

    @Test
    void blockedStaticMethodReturnsNewInstance() {
        var blocked = Ports.RateLimitResult.blocked("Rate limit exceeded");
        assertNotNull(blocked);
        assertTrue(blocked instanceof Ports.RateLimitResult.Blocked);
        assertEquals("Rate limit exceeded", blocked.message());
    }
}
