package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EnableTotpCommandTest {

    private static final UserId USER_ID = UserId.of(UUID.randomUUID());

    @Test
    void constructorAllowsValidSixDigitCode() {
        final EnableTotpCommand cmd = new EnableTotpCommand(USER_ID, "123456");
        assertEquals(USER_ID, cmd.userId());
        assertEquals("123456", cmd.totpCode());
    }

    @Test
    void constructorAllowsLeadingZeroCode() {
        // "005924" is a real RFC 6238 test vector — must not be treated as malformed
        final EnableTotpCommand cmd = new EnableTotpCommand(USER_ID, "005924");
        assertEquals("005924", cmd.totpCode());
    }

    @Test
    void constructorRejectsNullUserId() {
        assertThrows(NullPointerException.class, () -> new EnableTotpCommand(null, "123456"));
    }

    @Test
    void constructorRejectsNullTotpCode() {
        assertThrows(NullPointerException.class, () -> new EnableTotpCommand(USER_ID, null));
    }

    @ParameterizedTest(name = "invalid code: \"{0}\"")
    @ValueSource(strings = {"", "12345", "1234567", "abcdef", "12345a", " 23456", "12 456"})
    void constructorRejectsMalformedCodes(final String badCode) {
        assertThrows(IllegalArgumentException.class, () -> new EnableTotpCommand(USER_ID, badCode));
    }
}

