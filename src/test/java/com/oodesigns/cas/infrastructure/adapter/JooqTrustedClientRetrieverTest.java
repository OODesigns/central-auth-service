package com.oodesigns.cas.infrastructure.adapter;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JooqTrustedClientRetrieverTest {

    @Test
    void constructorRejectsNullDsl() {
        assertThrows(NullPointerException.class, () -> new JooqTrustedClientRetriever(null));
    }

    @Test
    void blankFingerprintReturnsEmptyWithoutQuery() {
        final DSLContext dsl = mock(DSLContext.class);
        final JooqTrustedClientRetriever retriever = new JooqTrustedClientRetriever(dsl);

        assertTrue(retriever.findByFingerprint(" ").isEmpty());
        assertTrue(retriever.findByFingerprint(null).isEmpty());
        verify(dsl, org.mockito.Mockito.never()).fetchOptional(anyString(), eq(" "));
    }

    @Test
    void mapsTrustedClientRecord() {
        final DSLContext dsl = mock(DSLContext.class);
        final Record record = mock(Record.class);
        final UUID id = UUID.randomUUID();
        final Instant expiresAt = Instant.now().plusSeconds(60);
        when(record.get("id", UUID.class)).thenReturn(id);
        when(record.get("fingerprint", String.class)).thenReturn("fingerprint");
        when(record.get("expires_at", Instant.class)).thenReturn(expiresAt);
        when(record.get("revoked_at", Instant.class)).thenReturn(null);
        when(dsl.fetchOptional(anyString(), eq("fingerprint"))).thenReturn(Optional.of(record));

        final Optional<com.oodesigns.cas.domain.service.Ports.TrustedClient> result =
                new JooqTrustedClientRetriever(dsl).findByFingerprint("fingerprint");

        assertTrue(result.isPresent());
        assertEquals(id, result.orElseThrow().id());
        assertEquals(expiresAt, result.orElseThrow().expiresAt());
    }
}