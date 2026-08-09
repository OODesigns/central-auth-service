package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DisableTotpCommandTest {

    @Test
    void constructorAllowsValidValues() {
        final UserId uid = UserId.of(UUID.randomUUID());
        final DisableTotpCommand cmd = new DisableTotpCommand(uid, "password123", DisableReason.USER_REQUESTED);
        assertNotNull(cmd);
        assertEquals(uid, cmd.userId());
        assertEquals("password123", cmd.password());
        assertEquals(DisableReason.USER_REQUESTED, cmd.reason());
    }

    @Test
    void constructorRejectsNulls() {
        final UserId uid = UserId.of(UUID.randomUUID());
        assertThrows(NullPointerException.class, () -> new DisableTotpCommand(null, "p", DisableReason.USER_REQUESTED));
        assertThrows(NullPointerException.class, () -> new DisableTotpCommand(uid, null, DisableReason.USER_REQUESTED));
        assertThrows(NullPointerException.class, () -> new DisableTotpCommand(uid, "p", null));
    }

    @Test
    void constructorRejectsBlankPassword() {
        final UserId uid = UserId.of(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> new DisableTotpCommand(uid, "", DisableReason.USER_REQUESTED));
        assertThrows(IllegalArgumentException.class, () -> new DisableTotpCommand(uid, "  ", DisableReason.USER_REQUESTED));
    }
}

