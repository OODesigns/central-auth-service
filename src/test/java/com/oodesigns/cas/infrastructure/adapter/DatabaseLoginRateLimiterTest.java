package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.application.command.LoginCommand;
import com.oodesigns.cas.domain.value.IpAddress;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.Username;
import java.time.Duration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseLoginRateLimiterTest {
    private static final String SQL = "SELECT api_schema.consume_login_rate_limit(?, ?, ?)";

    @Test
    void consumesAllThreeSharedBuckets() {
        final DSLContext dsl = mock(DSLContext.class);
        final LoginCommand command = command();
        final Record allowed = result(true);
        when(dsl.fetchOne(SQL, "login:ip:192.0.2.10", 5, 60)).thenReturn(allowed);
        when(dsl.fetchOne(SQL, "login:id:test_user", 5, 60)).thenReturn(allowed);
        when(dsl.fetchOne(SQL, "login:ip+id:192.0.2.10:test_user", 5, 60)).thenReturn(allowed);

        assertTrue(allowed(new DatabaseLoginRateLimiter(dsl).checkLimit(command)));
        verify(dsl).fetchOne(SQL, "login:ip+id:192.0.2.10:test_user", 5, 60);
    }

    @Test
    void stopsAfterFirstBlockedBucket() {
        final DSLContext dsl = mock(DSLContext.class);
        final Record blocked = result(false);
        when(dsl.fetchOne(SQL, "login:ip:192.0.2.10", 5, 60)).thenReturn(blocked);

        assertFalse(allowed(new DatabaseLoginRateLimiter(dsl).checkLimit(command())));
        verify(dsl, never()).fetchOne(SQL, "login:id:test_user", 5, 60);
    }

    @Test
    void stopsAfterUsernameBucketIsBlocked() {
        final DSLContext dsl = mock(DSLContext.class);
        final Record allowed = result(true);
        final Record blocked = result(false);
        when(dsl.fetchOne(SQL, "login:ip:192.0.2.10", 5, 60)).thenReturn(allowed);
        when(dsl.fetchOne(SQL, "login:id:test_user", 5, 60)).thenReturn(blocked);

        assertFalse(allowed(new DatabaseLoginRateLimiter(dsl).checkLimit(command())));
        verify(dsl, never()).fetchOne(SQL, "login:ip+id:192.0.2.10:test_user", 5, 60);
    }

    @Test
    void failsClosedWhenDatabaseIsUnavailableOrReturnsNoRow() {
        final DSLContext failingDsl = mock(DSLContext.class);
        when(failingDsl.fetchOne(SQL, "login:ip:192.0.2.10", 5, 60))
            .thenThrow(new IllegalStateException("unavailable"));
        assertFalse(allowed(new DatabaseLoginRateLimiter(failingDsl).checkLimit(command())));

        final DSLContext emptyDsl = mock(DSLContext.class);
        assertFalse(allowed(new DatabaseLoginRateLimiter(emptyDsl).checkLimit(command())));
    }

    @Test
    void validatesConstructorAndCommand() {
        final DSLContext dsl = mock(DSLContext.class);
        assertThrows(NullPointerException.class, () -> new DatabaseLoginRateLimiter(null));
        assertThrows(IllegalArgumentException.class,
            () -> new DatabaseLoginRateLimiter(dsl, 0, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class,
            () -> new DatabaseLoginRateLimiter(dsl, 1, null));
        assertThrows(IllegalArgumentException.class,
            () -> new DatabaseLoginRateLimiter(dsl, 1, Duration.ofMillis(500)));
        assertThrows(IllegalArgumentException.class,
            () -> new DatabaseLoginRateLimiter(dsl, 1, Duration.ofSeconds((long) Integer.MAX_VALUE + 1)));
        assertThrows(IllegalArgumentException.class,
            () -> new DatabaseLoginRateLimiter(dsl, 1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
            () -> new DatabaseLoginRateLimiter(dsl, 1, Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class,
            () -> new DatabaseLoginRateLimiter(dsl, 1, Duration.ofMillis(999)));
        assertThrows(IllegalArgumentException.class,
            () -> new DatabaseLoginRateLimiter(dsl).checkLimit(null));
    }

    private LoginCommand command() {
        return new LoginCommand(
            Username.of("test_user"),
            Password.of("ValidPassword123!".toCharArray()),
            IpAddress.of("192.0.2.10"));
    }

    private Record result(final boolean value) {
        final Record record = mock(Record.class);
        when(record.get(0, Boolean.class)).thenReturn(value);
        return record;
    }

    private boolean allowed(final com.oodesigns.cas.domain.service.Ports.RateLimitResult result) {
        return result.mapTo(ignored -> true).orElse(ignored -> false);
    }
}