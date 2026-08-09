package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DisableTotpCommandTest {

    private static final String VALID_PASSWORD = "ValidPassword1234";

    @Test
    void constructorAllowsValidValues() {
        final UserId uid = UserId.of(UUID.randomUUID());
        final Password password = Password.of(VALID_PASSWORD);
        final DisableTotpCommand cmd = new DisableTotpCommand(uid, password, DisableReason.USER_REQUESTED);

        assertNotNull(cmd);
        assertEquals(uid, cmd.userId());
        assertSame(password, cmd.password());
        assertEquals(DisableReason.USER_REQUESTED, cmd.reason());
    }

    @Test
    void constructorRejectsNulls() {
        final UserId uid = UserId.of(UUID.randomUUID());
        final Password password = Password.of(VALID_PASSWORD);

        assertThrows(NullPointerException.class,
            () -> new DisableTotpCommand(null, password, DisableReason.USER_REQUESTED));
        assertThrows(NullPointerException.class,
            () -> new DisableTotpCommand(uid, null, DisableReason.USER_REQUESTED));
        assertThrows(NullPointerException.class,
            () -> new DisableTotpCommand(uid, password, null));
    }

    @Test
    void passwordValueObjectRejectsBlankAndShortPasswords() {
        // Blank/short password rules are enforced by Password.of, so DisableTotpCommand
        // can never be constructed with an invalid password.
        assertThrows(IllegalArgumentException.class, () -> Password.of(""));
        assertThrows(IllegalArgumentException.class, () -> Password.of("              "));
        assertThrows(IllegalArgumentException.class, () -> Password.of("short"));
    }
}

