package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.Payload;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MockTokenSignerTest {

    @Test
    void incrementsCounterAndFormatsToken() {
        final MockTokenSigner signer = new MockTokenSigner();
        final Instant now = Instant.now();

        final String t1 = signer.signAccessToken(Payload.of("p1"), now).orElseThrow().value();
        final String t2 = signer.signAccessToken(Payload.of("p2"), now).orElseThrow().value();

        assertEquals("mock.1.p1", t1);
        assertEquals("mock.2.p2", t2);
        assertEquals(2, signer.getSignedTokenCount());
    }

    @Test
    void resetClearsCounter() {
        final MockTokenSigner signer = new MockTokenSigner();
        final Instant now = Instant.now();
        signer.signAccessToken(Payload.of("p1"), now);
        signer.signAccessToken(Payload.of("p2"), now);

        signer.reset();

        assertEquals(0, signer.getSignedTokenCount());
        final String t1 = signer.signAccessToken(Payload.of("p3"), now).orElseThrow().value();
        assertEquals("mock.1.p3", t1);
    }

    @Test
    void returnsEmptyWhenPayloadOrExpiryNull() {
        final MockTokenSigner signer = new MockTokenSigner();
        final Instant now = Instant.now();

        assertTrue(signer.signAccessToken(null, now).isEmpty());
        assertTrue(signer.signAccessToken(Payload.of("p"), null).isEmpty());
    }
}
