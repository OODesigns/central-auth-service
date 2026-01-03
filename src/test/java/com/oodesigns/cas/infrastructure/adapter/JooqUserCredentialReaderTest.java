package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.UserCredential;
import com.oodesigns.cas.domain.value.Username;
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
class JooqUserCredentialReaderTest {

    @Mock
    private DSLContext dsl;

    private JooqUserCredentialReader reader;

    @BeforeEach
    void setUp() {
        reader = new JooqUserCredentialReader(dsl);
    }

    @Test
    void findCredentialsByUsername_ReturnsEmpty_WhenUsernameIsNull() {
        Optional<UserCredential> result = reader.findCredentialsByUsername(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void findCredentialsByUsername_ReturnsEmpty_WhenNoRecordFound() {
        Username username = new Username("user");
        when(dsl.fetchOptional(any(String.class), any(Object.class))).thenReturn(Optional.empty());

        Optional<UserCredential> result = reader.findCredentialsByUsername(username);
        assertTrue(result.isEmpty());
    }

    @Test
    void findCredentialsByUsername_ReturnsCredential_WhenRecordFound() {
        Username username = new Username("user");
        UUID userId = UUID.randomUUID();
        // Valid bcrypt hash format: $2a$ + cost + 22 char salt + 31 char hash = 60 chars
        String hash = "$2a$10$12345678901234567890123456789012345678901234567890123";

        Record jooqRecord = mock(Record.class);
        when(jooqRecord.get("user_id", UUID.class)).thenReturn(userId);
        when(jooqRecord.get("password_hash", String.class)).thenReturn(hash);

        when(dsl.fetchOptional("SELECT * FROM auth.find_user_credentials(?)", "user"))
                .thenReturn(Optional.of(jooqRecord));

        Optional<UserCredential> result = reader.findCredentialsByUsername(username);

        assertTrue(result.isPresent());
        assertEquals(userId, result.get().userId().value());
        assertEquals(hash, result.get().passwordHash().asString());
    }
    
    @Test
    void constructor_Throws_WhenDslIsNull() {
        assertThrows(NullPointerException.class, () -> new JooqUserCredentialReader(null));
    }
}
