package com.oodesigns.cas.application.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisableTotpResultTest {

    @Test
    void successMapperReturnsValue() {
        final DisableTotpResult res = DisableTotpResult.success();
        final String out = res.mapTo(success -> "OK").orElse(failure -> "ERR");
        assertEquals("OK", out);
    }

    @Test
    void failureMapperReturnsValue() {
        final DisableTotpResult res = DisableTotpResult.failure("CODE", "msg");
        final String out = res.mapTo(success -> "OK").orElse(failure -> failure.errorCode() + ":" + failure.errorMessage());
        assertEquals("CODE:msg", out);
    }

    @Test
    void failureValidationRejectsNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> DisableTotpResult.failure(null, "msg"));
        assertThrows(IllegalArgumentException.class, () -> DisableTotpResult.failure("", "msg"));
        assertThrows(IllegalArgumentException.class, () -> DisableTotpResult.failure("C", null));
        assertThrows(IllegalArgumentException.class, () -> DisableTotpResult.failure("C", ""));
    }

    @Test
    void disableReasonEnumValues() {
        final DisableReason[] values = DisableReason.values();
        assertTrue(values.length >= 4);
        // ensure valueOf works for known constant
        assertEquals(DisableReason.USER_REQUESTED, DisableReason.valueOf("USER_REQUESTED"));
    }
}

