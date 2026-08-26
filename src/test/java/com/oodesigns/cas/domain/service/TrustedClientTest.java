package com.oodesigns.cas.domain.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustedClientTest {
    private static final UUID ID = UUID.randomUUID();
    private static final String FINGERPRINT = "fingerprint";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void constructorRejectsMissingIdentity() {
        assertThrows(NullPointerException.class,
                () -> new Ports.TrustedClient(null, FINGERPRINT, null, null));
        assertThrows(NullPointerException.class,
                () -> new Ports.TrustedClient(ID, null, null, null));
    }

    @Test
    void activeClientRequiresUnrevokedUnexpiredCertificate() {
        assertTrue(new Ports.TrustedClient(ID, FINGERPRINT, null, null).isActive(NOW));
        assertTrue(new Ports.TrustedClient(ID, FINGERPRINT, NOW.plusSeconds(1), null).isActive(NOW));
        assertFalse(new Ports.TrustedClient(ID, FINGERPRINT, NOW, null).isActive(NOW));
        assertFalse(new Ports.TrustedClient(ID, FINGERPRINT, NOW.plusSeconds(1), NOW).isActive(NOW));
    }
}