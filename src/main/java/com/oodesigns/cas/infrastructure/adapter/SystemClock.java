package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;

import java.time.Instant;

/**
 * Real system clock implementation.
 * Returns the current system time using Instant.now().
 * <p>
 * This is the production implementation for clock services.
 * For testing, use MockClock to control time deterministically.
 */
public final class SystemClock implements Ports.Clock {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
