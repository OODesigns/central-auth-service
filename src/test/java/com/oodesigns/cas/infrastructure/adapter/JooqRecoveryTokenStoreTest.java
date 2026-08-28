package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.PasswordHash;
import com.oodesigns.cas.domain.value.RecoveryToken;
import com.oodesigns.cas.domain.value.UserId;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class JooqRecoveryTokenStoreTest {
    private static final String ISSUE_SQL = "SELECT api_schema.issue_recovery_token(?, ?, ?)";
    private static final String COMPLETE_SQL = "SELECT api_schema.consume_recovery_token(?, ?, ?)";
    private static final String TOKEN = "header.payload.signature";
    private static final PasswordHash PASSWORD_HASH = PasswordHash.of(
            "$2a$10$12345678901234567890123456789012345678901234567890123");

    @Mock
    private DSLContext dsl;

    private JooqRecoveryTokenStore store;

    @BeforeEach
    void setUp() {
        store = new JooqRecoveryTokenStore(dsl);
    }

    @Test
    void constructorRejectsNullDsl() {
        assertThrows(NullPointerException.class, () -> new JooqRecoveryTokenStore(null));
    }

    @Test
    void issueHashesTokenAndPassesIdentifiersToDatabase() {
        final UUID administratorId = UUID.randomUUID();
        final UUID targetUserId = UUID.randomUUID();

        store.issue(UserId.of(administratorId), UserId.of(targetUserId), RecoveryToken.of(TOKEN));

        verify(dsl).execute(ISSUE_SQL, administratorId, targetUserId,
            "256d04db4e5e4ac308751ed0885b722b758630567c53a7125ed9fbd068e5c3f6");
    }

    @Test
    void issueRejectsNullArguments() {
        final UserId userId = UserId.of(UUID.randomUUID());
        final RecoveryToken token = RecoveryToken.of(TOKEN);
        assertThrows(NullPointerException.class, () -> store.issue(null, userId, token));
        assertThrows(NullPointerException.class, () -> store.issue(userId, null, token));
        assertThrows(NullPointerException.class, () -> store.issue(userId, userId, null));
    }

    @Test
    void consumeReturnsCompletedWhenDatabaseCompletes() {
        final Record record = mock(Record.class);
        when(record.get(0, String.class)).thenReturn("COMPLETED");
        final UserId userId = UserId.of(UUID.randomUUID());
        when(dsl.fetchOne(COMPLETE_SQL, userId.asUUID(),
            "256d04db4e5e4ac308751ed0885b722b758630567c53a7125ed9fbd068e5c3f6",
            PASSWORD_HASH.value())).thenReturn(record);

        assertEquals(Ports.RecoveryTokenStore.RecoveryCompletion.COMPLETED,
            store.consumeAndReset(userId, RecoveryToken.of(TOKEN), PASSWORD_HASH));
    }

    @Test
    void consumeReturnsInvalidWhenDatabaseReturnsOtherStatusOrNoRow() {
        final Record record = mock(Record.class);
        when(record.get(0, String.class)).thenReturn("INVALID_OR_CONSUMED");
        final UserId userId = UserId.of(UUID.randomUUID());
        when(dsl.fetchOne(COMPLETE_SQL, userId.asUUID(),
            "256d04db4e5e4ac308751ed0885b722b758630567c53a7125ed9fbd068e5c3f6",
            PASSWORD_HASH.value())).thenReturn(record);
        assertEquals(Ports.RecoveryTokenStore.RecoveryCompletion.INVALID_OR_CONSUMED,
                store.consumeAndReset(userId, RecoveryToken.of(TOKEN), PASSWORD_HASH));

        when(dsl.fetchOne(COMPLETE_SQL, userId.asUUID(),
            "256d04db4e5e4ac308751ed0885b722b758630567c53a7125ed9fbd068e5c3f6",
            PASSWORD_HASH.value())).thenReturn(null);
        assertEquals(Ports.RecoveryTokenStore.RecoveryCompletion.INVALID_OR_CONSUMED,
                store.consumeAndReset(userId, RecoveryToken.of(TOKEN), PASSWORD_HASH));
    }

    @Test
    void consumeRejectsNullArguments() {
        final UserId userId = UserId.of(UUID.randomUUID());
        final RecoveryToken token = RecoveryToken.of(TOKEN);
        assertThrows(NullPointerException.class, () -> store.consumeAndReset(null, token, PASSWORD_HASH));
        assertThrows(NullPointerException.class, () -> store.consumeAndReset(userId, null, PASSWORD_HASH));
        assertThrows(NullPointerException.class, () -> store.consumeAndReset(userId, token, null));
    }

    @Test
    void failsClosedWhenSha256IsUnavailable() {
        try (var mockedDigest = mockStatic(MessageDigest.class)) {
            mockedDigest.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("unavailable"));

            assertThrows(IllegalStateException.class,
                    () -> store.issue(UserId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()), RecoveryToken.of(TOKEN)));
        }
    }
}
