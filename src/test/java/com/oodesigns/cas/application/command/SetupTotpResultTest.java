package com.oodesigns.cas.application.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SetupTotpResultTest {

    private static final String VALID_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final String VALID_URI = "otpauth://totp/MyService:alice?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=MyService";

    // ---------------------------------------------------------------- SuccessResult

    @Test
    void successResultStoresValues() {
        final SetupTotpResult.SuccessResult result = SetupTotpResult.success(VALID_SECRET, VALID_URI);
        assertEquals(VALID_SECRET, result.secret());
        assertEquals(VALID_URI, result.otpauthUri());
    }

    @Test
    void successResultMapToAppliesMapper() {
        final SetupTotpResult result = SetupTotpResult.success(VALID_SECRET, VALID_URI);
        final String mapped = result.mapTo(s -> "ok-" + s.secret()).orElse(f -> "fail");
        assertEquals("ok-" + VALID_SECRET, mapped);
    }

    @Test
    void successResultOrElseIgnoresFailureMapper() {
        final SetupTotpResult result = SetupTotpResult.success(VALID_SECRET, VALID_URI);
        final String value = result.mapTo(s -> "success").orElse(f -> { fail("Should not reach failure mapper"); return null; });
        assertEquals("success", value);
    }

    @Test
    void successResultRejectsNullSecret() {
        assertThrows(NullPointerException.class, () -> SetupTotpResult.success(null, VALID_URI));
    }

    @Test
    void successResultRejectsBlankSecret() {
        assertThrows(IllegalArgumentException.class, () -> SetupTotpResult.success("   ", VALID_URI));
    }

    @Test
    void successResultRejectsNullUri() {
        assertThrows(NullPointerException.class, () -> SetupTotpResult.success(VALID_SECRET, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "http://example.com", "totp://something"})
    void successResultRejectsUriNotStartingWithOtpauthTotp(final String badUri) {
        assertThrows(IllegalArgumentException.class, () -> SetupTotpResult.success(VALID_SECRET, badUri));
    }

    // ---------------------------------------------------------------- FailureResult

    @Test
    void failureResultStoresValues() {
        final SetupTotpResult.FailureResult result = SetupTotpResult.failure("INTERNAL_ERROR", "boom");
        assertEquals("INTERNAL_ERROR", result.errorCode());
        assertEquals("boom", result.errorMessage());
    }

    @Test
    void failureResultMapToSkipsSuccessMapper() {
        final SetupTotpResult result = SetupTotpResult.failure("INTERNAL_ERROR", "boom");
        final String mapped = result.<String>mapTo(s -> { fail("Should not apply success mapper"); return null; })
            .orElse(f -> "failed-" + f.errorCode());
        assertEquals("failed-INTERNAL_ERROR", mapped);
    }

    @Test
    void failureResultRejectsBlankErrorCode() {
        assertThrows(IllegalArgumentException.class, () -> SetupTotpResult.failure("", "msg"));
        assertThrows(IllegalArgumentException.class, () -> SetupTotpResult.failure("  ", "msg"));
        assertThrows(IllegalArgumentException.class, () -> SetupTotpResult.failure(null, "msg"));
    }

    @Test
    void failureResultRejectsBlankErrorMessage() {
        assertThrows(IllegalArgumentException.class, () -> SetupTotpResult.failure("CODE", ""));
        assertThrows(IllegalArgumentException.class, () -> SetupTotpResult.failure("CODE", "  "));
        assertThrows(IllegalArgumentException.class, () -> SetupTotpResult.failure("CODE", null));
    }
}



