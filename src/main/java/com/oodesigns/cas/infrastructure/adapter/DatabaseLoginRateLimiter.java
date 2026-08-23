package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.application.command.LoginCommand;
import com.oodesigns.cas.domain.service.Ports;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jooq.DSLContext;

/** Shared login rate limiter backed by an atomic PostgreSQL function. */
public final class DatabaseLoginRateLimiter implements Ports.RateLimiter {
    private static final Logger LOGGER = Logger.getLogger(DatabaseLoginRateLimiter.class.getName());
    private static final String SQL = "SELECT api_schema.consume_login_rate_limit(?, ?, ?)";

    private final DSLContext dsl;
    private final int maxAttempts;
    private final int windowSeconds;

    public DatabaseLoginRateLimiter(final DSLContext dsl) {
        this(dsl, 5, Duration.ofMinutes(1));
    }

    DatabaseLoginRateLimiter(final DSLContext dsl, final int maxAttempts, final Duration window) {
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
    public Ports.RateLimitResult checkLimit(final LoginCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("LoginCommand cannot be null");
        }
        final String ip = command.ipAddress().value();
        final String username = command.username().value();
        return consume("login:ip:" + ip)
            .mapTo(allowed -> consume("login:id:" + username)
                .mapTo(alsoAllowed -> consume("login:ip+id:" + ip + ":" + username))
                .orElse(blocked -> blocked))
            .orElse(blocked -> blocked);
    }

    private Ports.RateLimitResult consume(final String key) {
        try {
            final boolean allowed = Optional.ofNullable(
                    dsl.fetchOne(SQL, key, maxAttempts, windowSeconds))
                .map(record -> record.get(0, Boolean.class))
                .orElse(false);
            return allowed ? Ports.RateLimitResult.allowed() : blocked();
        } catch (final RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Distributed login rate limiter is unavailable", exception);
            return blocked();
        }
    }

    private Ports.RateLimitResult blocked() {
        return Ports.RateLimitResult.blocked("Rate limit exceeded. Try again later.");
    }
}