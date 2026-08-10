package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.UserCredential;
import com.oodesigns.cas.domain.value.UserId;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JooqUserCredentialByIdReaderTest {

    @Mock
    private DSLContext dsl;

    private JooqUserCredentialByIdReader reader;

    @BeforeEach
    void setUp() {
        reader = new JooqUserCredentialByIdReader(dsl);
    }

    @Test
    void constructor_ThrowsNullPointerException_WhenDslIsNull() {
        assertThrows(NullPointerException.class,
                () -> new JooqUserCredentialByIdReader(null));
    }

    @Test
    void findCredentialsByUserId_ReturnsEmpty_WhenUserIdIsNull() {
        final Optional<UserCredential> result = reader.findCredentialsByUserId(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void findCredentialsByUserId_ReturnsEmpty_WhenNoRecordFound() {
        final UserId userId = UserId.of(UUID.randomUUID());
        when(dsl.fetchOptional(any(String.class), any(Object.class)))
                .thenReturn(Optional.empty());

        final Optional<UserCredential> result = reader.findCredentialsByUserId(userId);
        assertTrue(result.isEmpty());
    }

    @Test
    void findCredentialsByUserId_ReturnsCredential_WhenRecordFound() {
        final UUID rawId = UUID.randomUUID();
        final UserId userId = UserId.of(rawId);
        // Valid bcrypt hash format
        final String hash = "$2a$10$12345678901234567890123456789012345678901234567890123";

        final Record jooqRecord = mock(Record.class);
        when(jooqRecord.get("user_id", UUID.class)).thenReturn(rawId);
        when(jooqRecord.get("password_hash", String.class)).thenReturn(hash);

        when(dsl.fetchOptional(
                "SELECT * FROM api_schema.find_user_credentials_by_id(?)", rawId))
                .thenReturn(Optional.of(jooqRecord));

        final Optional<UserCredential> result = reader.findCredentialsByUserId(userId);

        assertTrue(result.isPresent());
        assertEquals(rawId, result.get().userId().value());
        assertEquals(hash, result.get().passwordHash().value());
    }
}

