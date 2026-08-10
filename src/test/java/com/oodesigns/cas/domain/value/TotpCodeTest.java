package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TotpCodeTest {

    // ---------------------------------------------------------------- valid construction

    @Test
    void ofAllowsSixDigitCode() {
        final TotpCode code = TotpCode.of("123456");
        assertEquals("123456", code.getCode());
        assertEquals("123456", code.value());
    }

    @Test
    void ofAllowsLeadingZeros() {
        // "005924" is a real RFC 6238 test vector — must not be rejected
        final TotpCode code = TotpCode.of("005924");
        assertEquals("005924", code.getCode());
    }

    @Test
    void ofAllowsAllZeros() {
        final TotpCode code = TotpCode.of("000000");
        assertEquals("000000", code.getCode());
    }

    // ---------------------------------------------------------------- validation

    @Test
    void ofRejectsNull() {
        assertThrows(NullPointerException.class, () -> TotpCode.of(null));
    }

    @ParameterizedTest(name = "invalid: \"{0}\"")
    @ValueSource(strings = {"", "12345", "1234567", "abcdef", "12345a", " 23456", "12 456", "OOOOOO"})
    void ofRejectsMalformedValues(final String bad) {
        assertThrows(IllegalArgumentException.class, () -> TotpCode.of(bad));
    }

    // ---------------------------------------------------------------- masking

    @Test
    void toStringMasksTheCode() {
        // OTP codes must not appear in log output
        final TotpCode code = TotpCode.of("287082");
        assertFalse(code.toString().contains("287082"),
            "toString() must not expose the code value");
        assertEquals("***", code.toString());
    }

    // ---------------------------------------------------------------- equality

    @Test
    void equalityIsValueBased() {
        assertEquals(TotpCode.of("123456"), TotpCode.of("123456"));
        assertNotEquals(TotpCode.of("123456"), TotpCode.of("654321"));
    }

    @Test
    void hashCodeIsConsistentWithEquals() {
        assertEquals(TotpCode.of("123456").hashCode(), TotpCode.of("123456").hashCode());
    }
}

