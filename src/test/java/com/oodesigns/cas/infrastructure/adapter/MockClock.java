package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import java.time.Instant;

/**
 * Mock implementation of Clock for testing.
 * Allows injecting specific Instant values for deterministic tests.
 */
public class MockClock implements Ports.Clock {
    private Instant currentTime;

    public MockClock(Instant initialTime) {
        this.currentTime = initialTime;
    }

    public MockClock() {
        this.currentTime = Instant.now();
    }

    @Override
    public Instant now() {
        return currentTime;
    }

    public void setCurrentTime(Instant instant) {
        this.currentTime = instant;
    }

    public void advanceSeconds(long seconds) {
        this.currentTime = currentTime.plusSeconds(seconds);
    }
}
