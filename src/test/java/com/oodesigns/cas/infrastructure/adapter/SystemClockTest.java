package com.oodesigns.cas.infrastructure.adapter;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SystemClock adapter.
 * Verifies the production clock implementation returns current time.
 */
class SystemClockTest {

    @Test
    void testNowReturnsCurrentInstant() {
        var clock = new SystemClock();
        
        Instant before = Instant.now();
        Instant clockTime = clock.now();
        Instant after = Instant.now();
        
        // Clock time should be between before and after
        assertFalse(clockTime.isBefore(before), "Clock time should not be before test start");
        assertFalse(clockTime.isAfter(after), "Clock time should not be after test end");
    }

    @Test
    void testNowReturnsNewInstantEachCall() {
        var clock = new SystemClock();
        
        Instant first = clock.now();
        Instant second = clock.now();
        
        assertFalse(second.isBefore(first), 
            "Second call should return same or later time");
    }

    @Test
    void testImplementsClockPort() {
        var clock = new SystemClock();
        
        assertInstanceOf(com.oodesigns.cas.domain.service.Ports.Clock.class, clock);
    }
}
