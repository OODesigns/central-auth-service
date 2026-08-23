package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.UserId;
import org.jooq.DSLContext;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Distributed per-user TOTP limiter backed by the atomic PostgreSQL limiter function. */
public final class DatabaseTotpRateLimiter implements Ports.TotpRateLimiter {
    private static final Logger LOGGER = Logger.getLogger(DatabaseTotpRateLimiter.class.getName());
    private static final String SQL = "SELECT api_schema.consume_login_rate_limit(?, ?, ?)";

    private final DSLContext dsl;
    private final int maxAttempts;
    private final int windowSeconds;

    public DatabaseTotpRateLimiter(final DSLContext dsl) {
        this(dsl, 5, Duration.ofMinutes(1));
    }

    DatabaseTotpRateLimiter(final DSLContext dsl, final int maxAttempts, final Duration window) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext cannot be null");
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()
            || window.toSeconds() < 1 || window.toSeconds() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Window must be a positive whole-second duration");
        }
        this.maxAttempts = maxAttempts;
        this.windowSeconds = Math.toIntExact(window.toSeconds());
    }

    @Override
    public Ports.RateLimitResult checkLimit(final UserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        try {
            final boolean allowed = Optional.ofNullable(
                    dsl.fetchOne(SQL, "totp:user:" + userId.value(), maxAttempts, windowSeconds))
                .map(record -> record.get(0, Boolean.class))
                .orElse(false);
            return allowed ? Ports.RateLimitResult.allowed() : blocked();
        } catch (final RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Distributed TOTP rate limiter is unavailable", exception);
            return blocked();
        }
    }

    private Ports.RateLimitResult blocked() {
        return Ports.RateLimitResult.blocked("2FA verification rate limit exceeded. Try again later.");
    }
}