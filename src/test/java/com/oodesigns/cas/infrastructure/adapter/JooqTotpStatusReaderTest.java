package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.UserId;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JooqTotpStatusReaderTest {

    @Mock
    private DSLContext dslContext;

    private JooqTotpStatusReader reader;

    @BeforeEach
    void setUp() {
        reader = new JooqTotpStatusReader(dslContext);
    }

    @Test
    void constructorRejectsNullDsl() {
        assertThrows(NullPointerException.class, () -> new JooqTotpStatusReader(null));
    }

    @Test
    void check2FAStatusReturnsEmptyWhenUserIdIsNull() {
        assertTrue(reader.check2FAStatus(null).isEmpty());
    }

    @Test
    void check2FAStatusReturnsEmptyWhenNoRecordFound() {
        final UUID userId = UUID.randomUUID();
        when(dslContext.fetchOptional("SELECT * FROM api_schema.get_totp_status(?)", userId))
            .thenReturn(Optional.empty());

        assertTrue(reader.check2FAStatus(UserId.of(userId)).isEmpty());
    }

    @Test
    void check2FAStatusReturnsUserIdWhenRecordFound() {
        final UUID userId = UUID.randomUUID();
        final Record record = mock(Record.class);
        when(record.get("user_id", UUID.class)).thenReturn(userId);
        when(dslContext.fetchOptional("SELECT * FROM api_schema.get_totp_status(?)", userId))
            .thenReturn(Optional.of(record));

        final Optional<UserId> result = reader.check2FAStatus(UserId.of(userId));

        assertTrue(result.isPresent());
        assertEquals(userId, result.get().value());
    }

    @Test
    void check2FAStatusUsesExpectedFunctionCall() {
        final UUID userId = UUID.randomUUID();
        final Record record = mock(Record.class);
        when(record.get("user_id", UUID.class)).thenReturn(userId);
        when(dslContext.fetchOptional("SELECT * FROM api_schema.get_totp_status(?)", userId))
            .thenReturn(Optional.of(record));

        reader.check2FAStatus(UserId.of(userId));

        verify(dslContext).fetchOptional("SELECT * FROM api_schema.get_totp_status(?)", userId);
    }
}


