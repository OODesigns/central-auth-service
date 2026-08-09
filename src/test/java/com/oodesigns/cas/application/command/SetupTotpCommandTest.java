package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.Username;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SetupTotpCommandTest {

    @Test
    void constructorAllowsValidValues() {
        final UserId uid = UserId.of(UUID.randomUUID());
        final Username username = Username.of("alice");
        final SetupTotpCommand cmd = new SetupTotpCommand(uid, username);

        assertNotNull(cmd);
        assertEquals(uid, cmd.userId());
        assertEquals(username, cmd.username());
    }

    @Test
    void constructorRejectsNulls() {
        final UserId uid = UserId.of(UUID.randomUUID());
        final Username username = Username.of("alice");

        assertThrows(NullPointerException.class, () -> new SetupTotpCommand(null, username));
        assertThrows(NullPointerException.class, () -> new SetupTotpCommand(uid, null));
    }
}

