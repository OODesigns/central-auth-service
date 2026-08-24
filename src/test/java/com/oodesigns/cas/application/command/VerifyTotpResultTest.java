package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.value.AccessToken;
import com.oodesigns.cas.domain.value.RefreshToken;
import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VerifyTotpResultTest {

    private static final UserId USER_ID = UserId.of(UUID.randomUUID());
    private static final Set<Permission> PERMISSIONS = Set.of(Permission.of("read"));
    private static final TokenService.TokenPair TOKEN_PAIR =
        new TokenService.TokenPair(AccessToken.of("access.token.here"), RefreshToken.of("refresh.token.here"));

    // ---------------------------------------------------------------- SuccessResult

    @Test
    void successResultStoresImmutableValues() {
        final VerifyTotpResult.SuccessResult result =
            VerifyTotpResult.success(TOKEN_PAIR, USER_ID, PERMISSIONS);
        assertEquals(TOKEN_PAIR, result.tokenPair());
        assertEquals(USER_ID, result.userId());
        assertEquals(PERMISSIONS, result.permissions());
        assertThrows(UnsupportedOperationException.class,
            () -> result.permissions().add(Permission.of("extra")));
    }

    @Test
    void successResultMapToAppliesMapper() {
        final VerifyTotpResult result = VerifyTotpResult.success(TOKEN_PAIR, USER_ID, PERMISSIONS);
        final String userId = result.mapTo(s -> s.userId().toString()).orElse(f -> "fail");
        assertEquals(USER_ID.toString(), userId);
    }

    @Test
    void successResultOrElseIgnoresFailureMapper() {
        final VerifyTotpResult result = VerifyTotpResult.success(TOKEN_PAIR, USER_ID, PERMISSIONS);
        final String value = result.mapTo(s -> "ok")
            .orElse(f -> { fail("Must not call failure mapper"); return null; });
        assertEquals("ok", value);
    }

    @Test
    void successResultRejectsNulls() {
        assertThrows(NullPointerException.class,
            () -> VerifyTotpResult.success(null, USER_ID, PERMISSIONS));
        assertThrows(NullPointerException.class,
            () -> VerifyTotpResult.success(TOKEN_PAIR, null, PERMISSIONS));
        assertThrows(NullPointerException.class,
            () -> VerifyTotpResult.success(TOKEN_PAIR, USER_ID, null));
    }

    // ---------------------------------------------------------------- FailureResult

    @Test
    void failureResultStoresValues() {
        final VerifyTotpResult.FailureResult result =
            VerifyTotpResult.failure("INVALID_TOTP_CODE", "bad code");
        assertEquals("INVALID_TOTP_CODE", result.errorCode());
        assertEquals("bad code", result.errorMessage());
    }

    @Test
    void failureResultMapToSkipsSuccessMapper() {
        final VerifyTotpResult result = VerifyTotpResult.failure("INVALID_TOTP_CODE", "bad code");
        final String value = result.<String>mapTo(s -> { fail("Must not call"); return null; })
            .orElse(f -> "err-" + f.errorCode());
        assertEquals("err-INVALID_TOTP_CODE", value);
    }

    @Test
    void failureResultRejectsNullAndBlankFields() {
        assertThrows(IllegalArgumentException.class,
            () -> VerifyTotpResult.failure(null, "msg"));
        assertThrows(IllegalArgumentException.class,
            () -> VerifyTotpResult.failure("", "msg"));
        assertThrows(IllegalArgumentException.class,
            () -> VerifyTotpResult.failure("  ", "msg"));
        assertThrows(IllegalArgumentException.class,
            () -> VerifyTotpResult.failure("CODE", null));
        assertThrows(IllegalArgumentException.class,
            () -> VerifyTotpResult.failure("CODE", ""));
        assertThrows(IllegalArgumentException.class,
            () -> VerifyTotpResult.failure("CODE", "  "));
    }
}

