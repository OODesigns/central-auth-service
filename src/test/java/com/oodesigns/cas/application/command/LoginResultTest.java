package com.oodesigns.cas.application.command;

import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LoginResult application result object.
 * Validates: result pattern, type-safe accessors, state consistency, permissions.
 */
public class LoginResultTest {

    @Test
    public void testSuccessResult() {
        LoginResult result = LoginResult.success("access_token_123", "refresh_token_456", new HashSet<>());

        assertTrue(result.isSuccess());
        assertEquals("access_token_123", result.getAccessToken());
        assertEquals("refresh_token_456", result.getRefreshToken());
        assertEquals(0, result.getPermissions().size());
    }

    @Test
    public void testFailureResult() {
        LoginResult result = LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password");

        assertFalse(result.isSuccess());
        assertEquals("INVALID_CREDENTIALS", result.getErrorCode());
        assertEquals("Invalid username or password", result.getErrorMessage());
    }

    @Test
    public void testAccessingTokenOnFailureThrows() {
        LoginResult result = LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password");

        assertThrows(IllegalStateException.class, result::getAccessToken);
        assertThrows(IllegalStateException.class, result::getRefreshToken);
    }

    @Test
    public void testAccessingErrorOnSuccessThrows() {
        LoginResult result = LoginResult.success("access_token", "refresh_token", new HashSet<>());

        assertThrows(IllegalStateException.class, result::getErrorCode);
        assertThrows(IllegalStateException.class, result::getErrorMessage);
    }

    @Test
    public void testSuccessWithNullTokensThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.success(null, "refresh_token", new HashSet<>()));
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.success("access_token", null, new HashSet<>()));
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.success(null, null, new HashSet<>()));
    }

    @Test
    public void testSuccessWithNullPermissionsThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.success("access_token", "refresh_token", null));
    }

    @Test
    public void testFailureWithNullErrorCodeThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.failure(null, "error message"));
    }

    @Test
    public void testFailureWithNullErrorMessageThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.failure("ERROR_CODE", null));
    }

    @Test
    public void testFailureWithBothNullThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.failure(null, null));
    }

    @Test
    public void testSuccessWithEmptyTokensThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.success("", "refresh_token", new HashSet<>()));
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.success("access_token", "", new HashSet<>()));
    }

    @Test
    public void testFailureWithEmptyErrorCodeThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.failure("", "error message"));
    }

    @Test
    public void testFailureWithEmptyErrorMessageThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.failure("ERROR_CODE", ""));
    }

    @Test
    public void testMultipleSuccessResults() {
        LoginResult result1 = LoginResult.success("token1", "refresh1", new HashSet<>());
        LoginResult result2 = LoginResult.success("token2", "refresh2", new HashSet<>());

        assertTrue(result1.isSuccess());
        assertTrue(result2.isSuccess());
        assertNotEquals(result1.getAccessToken(), result2.getAccessToken());
    }

    @Test
    public void testMultipleFailureResults() {
        LoginResult result1 = LoginResult.failure("ERROR_1", "message 1");
        LoginResult result2 = LoginResult.failure("ERROR_2", "message 2");

        assertFalse(result1.isSuccess());
        assertFalse(result2.isSuccess());
        assertNotEquals(result1.getErrorCode(), result2.getErrorCode());
    }

    @Test
    public void testCannotSwitchStates() {
        LoginResult success = LoginResult.success("token", "refresh", new HashSet<>());
        LoginResult failure = LoginResult.failure("CODE", "message");

        // Success cannot be failed and vice versa
        assertTrue(success.isSuccess());
        assertFalse(failure.isSuccess());
    }
}
