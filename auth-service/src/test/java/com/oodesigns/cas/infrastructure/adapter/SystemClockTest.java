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
        final var clock = new SystemClock();
        
        final Instant before = Instant.now();
        final Instant clockTime = clock.now();
        final Instant after = Instant.now();
        
        // Clock time should be between before and after
        assertFalse(clockTime.isBefore(before), "Clock time should not be before test start");
        assertFalse(clockTime.isAfter(after), "Clock time should not be after test end");
    }

    @Test
    void testNowReturnsNewInstantEachCall() {
        final var clock = new SystemClock();
        
        final Instant first = clock.now();
        final Instant second = clock.now();
        
        assertFalse(second.isBefore(first), 
            "Second call should return same or later time");
    }

    @Test
    void testImplementsClockPort() {
        final var clock = new SystemClock();
        
        assertInstanceOf(com.oodesigns.cas.domain.service.Ports.Clock.class, clock);
    }
}
