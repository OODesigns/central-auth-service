package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.value.AccessToken;
import com.oodesigns.cas.domain.value.RefreshToken;
import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class RefreshTokenResultTest {

    private static final TokenService.TokenPair PAIR =
        new TokenService.TokenPair(AccessToken.of("access.token.here"), RefreshToken.of("refresh.token.here"));
    private static final UserId USER_ID = UserId.of(UUID.randomUUID());
    private static final Set<Permission> PERMS = Set.of(Permission.of("read"));

    @Test
    void successMapsToSuccessBranch() {
        final RefreshTokenResult result = RefreshTokenResult.success(PAIR, USER_ID, PERMS);

        final String outcome = result
            .mapTo(s -> "success:" + s.userId())
            .orElse(f -> "failure:" + f.errorCode());

        assertEquals("success:" + USER_ID, outcome);
    }

    @Test
    void successExposesTokenPairUserIdAndPermissions() {
        final RefreshTokenResult.SuccessResult success =
            (RefreshTokenResult.SuccessResult) RefreshTokenResult.success(PAIR, USER_ID, PERMS);

        assertEquals(PAIR, success.tokenPair());
        assertEquals(USER_ID, success.userId());
        assertEquals(PERMS, success.permissions());
    }

    @Test
    void failureMapsToFailureBranch() {
        final RefreshTokenResult result =
            RefreshTokenResult.failure("INVALID_REFRESH_TOKEN", "bad token");

        final String outcome = result
            .mapTo(s -> { fail("should not map to success"); return "success"; })
            .orElse(f -> f.errorCode() + ":" + f.errorMessage());

        assertEquals("INVALID_REFRESH_TOKEN:bad token", outcome);
    }

    @Test
    void successPermissionsAreDefensivelyCopied() {
        final java.util.Set<Permission> mutable = new java.util.HashSet<>();
        mutable.add(Permission.of("read"));
        final RefreshTokenResult.SuccessResult success =
            (RefreshTokenResult.SuccessResult) RefreshTokenResult.success(PAIR, USER_ID, mutable);

        assertThrows(UnsupportedOperationException.class,
            () -> success.permissions().add(Permission.of("write")));
    }

    @Test
    void successRejectsNulls() {
        assertThrows(NullPointerException.class, () -> RefreshTokenResult.success(null, USER_ID, PERMS));
        assertThrows(NullPointerException.class, () -> RefreshTokenResult.success(PAIR, null, PERMS));
        assertThrows(NullPointerException.class, () -> RefreshTokenResult.success(PAIR, USER_ID, null));
    }

    @Test
    void failureRejectsBlankErrorCode() {
        assertThrows(IllegalArgumentException.class, () -> RefreshTokenResult.failure(" ", "msg"));
        assertThrows(IllegalArgumentException.class, () -> RefreshTokenResult.failure(null, "msg"));
    }

    @Test
    void failureRejectsBlankErrorMessage() {
        assertThrows(IllegalArgumentException.class, () -> RefreshTokenResult.failure("CODE", " "));
        assertThrows(IllegalArgumentException.class, () -> RefreshTokenResult.failure("CODE", null));
    }
}

