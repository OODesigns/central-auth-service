package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.UserId;
import java.time.Duration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseTotpRateLimiterTest {
    private static final String SQL = "SELECT api_schema.consume_login_rate_limit(?, ?, ?)";
    private static final UserId USER_ID = UserId.of("00000000-0000-0000-0000-000000000001");

    @Test
    void allowsWhenDatabaseAllows() {
        final DSLContext dsl = mock(DSLContext.class);
        final Record allowed = result(true);
        when(dsl.fetchOne(SQL, "totp:user:00000000-0000-0000-0000-000000000001", 5, 60))
            .thenReturn(allowed);

        assertTrue(allowed(new DatabaseTotpRateLimiter(dsl).checkLimit(USER_ID)));
    }

    @Test
    void blocksWhenDatabaseBlocksOrReturnsNoRow() {
        final DSLContext blockedDsl = mock(DSLContext.class);
        final Record blocked = result(false);
        when(blockedDsl.fetchOne(SQL, "totp:user:00000000-0000-0000-0000-000000000001", 5, 60))
            .thenReturn(blocked);
        assertFalse(allowed(new DatabaseTotpRateLimiter(blockedDsl).checkLimit(USER_ID)));

        final DSLContext emptyDsl = mock(DSLContext.class);
        assertFalse(allowed(new DatabaseTotpRateLimiter(emptyDsl).checkLimit(USER_ID)));
    }

    @Test
    void failsClosedWhenDatabaseIsUnavailable() {
        final DSLContext dsl = mock(DSLContext.class);
        when(dsl.fetchOne(SQL, "totp:user:00000000-0000-0000-0000-000000000001", 5, 60))
            .thenThrow(new IllegalStateException("unavailable"));

        assertFalse(allowed(new DatabaseTotpRateLimiter(dsl).checkLimit(USER_ID)));
    }

    @Test
    void validatesConstructorAndUserId() {
        final DSLContext dsl = mock(DSLContext.class);
        assertThrows(NullPointerException.class, () -> new DatabaseTotpRateLimiter(null));
        assertThrows(IllegalArgumentException.class,
            () -> new DatabaseTotpRateLimiter(dsl, 0, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class,
            () -> new DatabaseTotpRateLimiter(dsl, 1, null));
        assertThrows(IllegalArgumentException.class,
            () -> new DatabaseTotpRateLimiter(dsl, 1, Duration.ofMillis(500)));
        assertThrows(IllegalArgumentException.class,
            () -> new DatabaseTotpRateLimiter(dsl, 1, Duration.ofSeconds((long) Integer.MAX_VALUE + 1)));
        assertThrows(IllegalArgumentException.class,
            () -> new DatabaseTotpRateLimiter(dsl, 1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
            () -> new DatabaseTotpRateLimiter(dsl, 1, Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class,
            () -> new DatabaseTotpRateLimiter(dsl, 1, Duration.ofMillis(999)));
        assertThrows(IllegalArgumentException.class,
            () -> new DatabaseTotpRateLimiter(dsl).checkLimit(null));
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
