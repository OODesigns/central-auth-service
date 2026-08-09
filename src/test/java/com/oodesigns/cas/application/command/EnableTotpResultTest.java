package com.oodesigns.cas.application.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnableTotpResultTest {

    private static final List<String> CODES = List.of("ABCD-EFGH-IJKL-MNOP", "QRST-UVWX-YZ23-4567");

    // ---------------------------------------------------------------- SuccessResult

    @Test
    void successResultStoresImmutableBackupCodes() {
        final EnableTotpResult.SuccessResult result = EnableTotpResult.success(CODES);
        assertEquals(CODES, result.backupCodes());
        assertThrows(UnsupportedOperationException.class, () -> result.backupCodes().add("extra"));
    }

    @Test
    void successResultMapToAppliesMapper() {
        final EnableTotpResult result = EnableTotpResult.success(CODES);
        final int size = result.mapTo(s -> s.backupCodes().size()).orElse(f -> -1);
        assertEquals(2, size);
    }

    @Test
    void successResultOrElseIgnoresFailureMapper() {
        final EnableTotpResult result = EnableTotpResult.success(CODES);
        final String value = result.mapTo(s -> "ok").orElse(f -> { fail("Must not call failure mapper"); return null; });
        assertEquals("ok", value);
    }

    @Test
    void successResultRejectsNullList() {
        assertThrows(NullPointerException.class, () -> EnableTotpResult.success(null));
    }

    @Test
    void successResultRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> EnableTotpResult.success(List.of()));
    }

    // ---------------------------------------------------------------- FailureResult

    @Test
    void failureResultStoresValues() {
        final EnableTotpResult.FailureResult result = EnableTotpResult.failure("INVALID_TOTP_CODE", "bad code");
        assertEquals("INVALID_TOTP_CODE", result.errorCode());
        assertEquals("bad code", result.errorMessage());
    }

    @Test
    void failureResultMapToSkipsSuccessMapper() {
        final EnableTotpResult result = EnableTotpResult.failure("INVALID_TOTP_CODE", "bad code");
        final String value = result.<String>mapTo(s -> { fail("Must not call success mapper"); return null; })
            .orElse(f -> "err-" + f.errorCode());
        assertEquals("err-INVALID_TOTP_CODE", value);
    }

    @Test
    void failureResultRejectsNullAndBlankErrorCode() {
        assertThrows(IllegalArgumentException.class, () -> EnableTotpResult.failure(null, "msg"));
        assertThrows(IllegalArgumentException.class, () -> EnableTotpResult.failure("", "msg"));
        assertThrows(IllegalArgumentException.class, () -> EnableTotpResult.failure("  ", "msg"));
    }

    @Test
    void failureResultRejectsNullAndBlankErrorMessage() {
        assertThrows(IllegalArgumentException.class, () -> EnableTotpResult.failure("CODE", null));
        assertThrows(IllegalArgumentException.class, () -> EnableTotpResult.failure("CODE", ""));
        assertThrows(IllegalArgumentException.class, () -> EnableTotpResult.failure("CODE", "  "));
    }
}

