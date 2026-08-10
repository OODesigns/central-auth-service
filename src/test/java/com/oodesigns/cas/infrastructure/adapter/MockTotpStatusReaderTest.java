package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.UserId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockTotpStatusReaderTest {

    @Test
    void check2FAStatusReflectsEnabledUsers() {
        final MockTotpStatusReader reader = new MockTotpStatusReader();
        final UserId userId = UserId.of(UUID.randomUUID());

        assertFalse(reader.check2FAStatus(userId).isPresent());
        reader.enable(userId);
        assertTrue(reader.check2FAStatus(userId).isPresent());
        reader.disable(userId);
        assertFalse(reader.check2FAStatus(userId).isPresent());
    }

    @Test
    void check2FAStatusRejectsNullUserId() {
        final MockTotpStatusReader reader = new MockTotpStatusReader();
        assertThrows(IllegalArgumentException.class, () -> reader.check2FAStatus(null));
    }
}

