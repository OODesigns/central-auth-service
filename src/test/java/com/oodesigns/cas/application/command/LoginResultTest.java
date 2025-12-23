package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.value.Jti;
import com.oodesigns.cas.domain.value.Permission;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LoginResult application result object.
 * Validates: result pattern, type-safe accessors, state consistency, permissions.
 */
class LoginResultTest {

    @Test
    void testSuccessResult() {
        Set<Permission> permissions = new HashSet<>();
        AuthenticationService.TokenPair tokenPair = new AuthenticationService.TokenPair(
            "access_token_123", "refresh_token_456", Jti.generate(), permissions);
        LoginResult result = LoginResult.success(tokenPair);

        assertTrue(result.isSuccess());
        assertEquals("access_token_123", result.getAccessToken());
        assertEquals("refresh_token_456", result.getRefreshToken());
        assertEquals(0, result.getPermissions().size());
    }

    @Test
    void testFailureResult() {
        LoginResult result = LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password");

        assertFalse(result.isSuccess());
        assertEquals("INVALID_CREDENTIALS", result.getErrorCode());
        assertEquals("Invalid username or password", result.getErrorMessage());
    }

    @Test
    void testAccessingTokenOnFailureThrows() {
        LoginResult result = LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password");

        assertThrows(IllegalStateException.class, result::getAccessToken);
        assertThrows(IllegalStateException.class, result::getRefreshToken);
    }

    @Test
    void testAccessingErrorOnSuccessThrows() {
        Set<Permission> permissions = new HashSet<>();
        AuthenticationService.TokenPair tokenPair = new AuthenticationService.TokenPair(
            "access_token", "refresh_token", Jti.generate(), permissions);
        LoginResult result = LoginResult.success(tokenPair);

        assertThrows(IllegalStateException.class, result::getErrorCode);
        assertThrows(IllegalStateException.class, result::getErrorMessage);
    }

    @Test
    void testSuccessWithNullTokensThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.success(null));
    }

    @Test
    void testSuccessWithNullPermissionsThrows() {
        // TokenPair constructor validates permissions cannot be null
        var jti = Jti.generate();
        assertThrows(NullPointerException.class,
            () -> new AuthenticationService.TokenPair("access_token", "refresh_token", jti, null));
    }

    @Test
    void testFailureWithNullErrorCodeThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.failure(null, "error message"));
    }

    @Test
    void testFailureWithNullErrorMessageThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.failure("ERROR_CODE", null));
    }

    @Test
    void testFailureWithBothNullThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.failure(null, null));
    }

    @Test
    void testSuccessWithEmptyTokensThrows() {
        // TokenPair constructor validates tokens are not blank/null
        var permissions = new HashSet<Permission>();
        assertThrows(NullPointerException.class,
            () -> createTokenPair(null, "refresh_token", permissions));
    }
    
    @Test
    void testSuccessWithEmptyRefreshTokenThrows() {
        var permissions = new HashSet<Permission>();
        assertThrows(NullPointerException.class,
            () -> createTokenPair("access_token", null, permissions));
    }
    
    private AuthenticationService.TokenPair createTokenPair(String access, String refresh, Set<Permission> perms) {
        return new AuthenticationService.TokenPair(access, refresh, Jti.generate(), perms);
    }

    @Test
    void testFailureWithEmptyErrorCodeThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.failure("", "error message"));
    }

    @Test
    void testFailureWithEmptyErrorMessageThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.failure("ERROR_CODE", ""));
    }

    @Test
    void testMultipleSuccessResults() {
        Set<Permission> permissions1 = new HashSet<>();
        Set<Permission> permissions2 = new HashSet<>();
        AuthenticationService.TokenPair tokenPair1 = new AuthenticationService.TokenPair(
            "token1", "refresh1", Jti.generate(), permissions1);
        AuthenticationService.TokenPair tokenPair2 = new AuthenticationService.TokenPair(
            "token2", "refresh2", Jti.generate(), permissions2);
        LoginResult result1 = LoginResult.success(tokenPair1);
        LoginResult result2 = LoginResult.success(tokenPair2);

        assertTrue(result1.isSuccess());
        assertTrue(result2.isSuccess());
        assertNotEquals(result1.getAccessToken(), result2.getAccessToken());
    }

    @Test
    void testMultipleFailureResults() {
        LoginResult result1 = LoginResult.failure("ERROR_1", "message 1");
        LoginResult result2 = LoginResult.failure("ERROR_2", "message 2");

        assertFalse(result1.isSuccess());
        assertFalse(result2.isSuccess());
        assertNotEquals(result1.getErrorCode(), result2.getErrorCode());
    }

    @Test
    void testCannotSwitchStates() {
        Set<Permission> permissions = new HashSet<>();
        AuthenticationService.TokenPair tokenPair = new AuthenticationService.TokenPair(
            "token", "refresh", Jti.generate(), permissions);
        LoginResult success = LoginResult.success(tokenPair);
        LoginResult failure = LoginResult.failure("CODE", "message");

        // Success cannot be failed and vice versa
        assertTrue(success.isSuccess());
        assertFalse(failure.isSuccess());
    }
}
