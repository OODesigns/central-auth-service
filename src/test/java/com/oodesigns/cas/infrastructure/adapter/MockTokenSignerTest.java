package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.Payload;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MockTokenSignerTest {

    @Test
    void incrementsCounterAndFormatsToken() {
        MockTokenSigner signer = new MockTokenSigner();
        Instant now = Instant.now();

        String t1 = signer.sign(Payload.of("p1"), now).orElseThrow();
        String t2 = signer.sign(Payload.of("p2"), now).orElseThrow();

        assertEquals("mock.1.p1", t1);
        assertEquals("mock.2.p2", t2);
        assertEquals(2, signer.getSignedTokenCount());
    }

    @Test
    void resetClearsCounter() {
        MockTokenSigner signer = new MockTokenSigner();
        Instant now = Instant.now();
        signer.sign(Payload.of("p1"), now);
        signer.sign(Payload.of("p2"), now);

        signer.reset();

        assertEquals(0, signer.getSignedTokenCount());
        String t1 = signer.sign(Payload.of("p3"), now).orElseThrow();
        assertEquals("mock.1.p3", t1);
    }

    @Test
    void returnsEmptyWhenPayloadOrExpiryNull() {
        MockTokenSigner signer = new MockTokenSigner();
        Instant now = Instant.now();

        assertTrue(signer.sign(null, now).isEmpty());
        assertTrue(signer.sign(Payload.of("p"), null).isEmpty());
    }
}
