package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.TotpCode;
import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EnableTotpCommandTest {

    private static final UserId USER_ID = UserId.of(UUID.randomUUID());

    @Test
    void constructorAllowsValidValues() {
        final TotpCode code = TotpCode.of("123456");
        final EnableTotpCommand cmd = new EnableTotpCommand(USER_ID, code);
        assertEquals(USER_ID, cmd.userId());
        assertSame(code, cmd.totpCode());
    }

    @Test
    void constructorRejectsNullUserId() {
        assertThrows(NullPointerException.class,
            () -> new EnableTotpCommand(null, TotpCode.of("123456")));
    }

    @Test
    void constructorRejectsNullTotpCode() {
        assertThrows(NullPointerException.class,
            () -> new EnableTotpCommand(USER_ID, null));
    }

    @Test
    void totpCodeValueObjectEnforcesValidation() {
        // TotpCode handles validation — these must reach TotpCode.of() and be rejected
        assertThrows(IllegalArgumentException.class, () -> TotpCode.of("12345"));     // 5 digits
        assertThrows(IllegalArgumentException.class, () -> TotpCode.of("1234567"));   // 7 digits
        assertThrows(IllegalArgumentException.class, () -> TotpCode.of("abcdef"));   // letters
        assertThrows(IllegalArgumentException.class, () -> TotpCode.of(""));         // empty
    }

    @Test
    void totpCodeValueObjectAcceptsLeadingZeros() {
        // "005924" is a real RFC 6238 test vector — must not be rejected
        final EnableTotpCommand cmd = new EnableTotpCommand(USER_ID, TotpCode.of("005924"));
        assertEquals("005924", cmd.totpCode().getCode());
    }
}

