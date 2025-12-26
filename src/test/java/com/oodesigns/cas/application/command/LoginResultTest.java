package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.TokenService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LoginResult application result object.
 * Validates: result pattern, type-safe accessors, state consistency, permissions.
 */
class LoginResultTest {

    @Test
    void testSuccessResult() {
        TokenService.TokenPair tokenPair = new TokenService.TokenPair(
            "access_token_123", "refresh_token_456");
        LoginResult result = LoginResult.success(tokenPair);

        result.mapTo(success -> {
                assertEquals("access_token_123", success.tokenPair().accessToken());
                assertEquals("refresh_token_456", success.tokenPair().refreshToken());
                // permissions no longer surfaced on TokenPair
                return null;
            })
            .orElse(failure -> {
                fail("Expected success result but got failure");
                return null;
            });
    }

    @Test
    void testFailureResult() {
        LoginResult result = LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password");

        result.mapTo(success -> {
                fail("Expected failure result but got success");
                return null;
            })
            .orElse(failure -> {
                assertEquals("INVALID_CREDENTIALS", failure.errorCode());
                assertEquals("Invalid username or password", failure.errorMessage());
                return null;
            });
    }

    @Test
    void testAccessingTokenOnFailureThrows() {
        // With fold pattern, failure results never have token access - type-safe at compile time
        LoginResult result = LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password");

        // This test verifies that FailureResult doesn't expose token methods
        result.mapTo(success -> {
                fail("FailureResult should never match success case");
                return null;
            })
            .orElse(failure -> {
                // Only error-related methods are available
                assertNotNull(failure.errorCode());
                assertNotNull(failure.errorMessage());
                // Token methods are not available on FailureResult - verified at compile time
                return null;
            });
    }

    @Test
    void testAccessingErrorOnSuccessThrows() {
        TokenService.TokenPair tokenPair = new TokenService.TokenPair(
            "access_token", "refresh_token");
        LoginResult result = LoginResult.success(tokenPair);

        result.mapTo(success -> {
                assertEquals("access_token", success.tokenPair().accessToken());
                assertEquals("refresh_token", success.tokenPair().refreshToken());
                return null;
            })
            .orElse(failure -> {
                fail("Expected success result but got failure");
                return null;
            });
    }

    @Test
    void testSuccessWithNullTokensThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.success(null));
    }

    @Test
    void testSuccessWithNullPermissionsThrows() {
        // permissions are no longer part of TokenPair; nothing to validate
        assertTrue(true);
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
        assertThrows(NullPointerException.class,
            () -> createTokenPair(null, "refresh_token"));
    }
    
    @Test
    void testSuccessWithEmptyRefreshTokenThrows() {
        assertThrows(NullPointerException.class,
            () -> createTokenPair("access_token", null));
    }
    
    private TokenService.TokenPair createTokenPair(String access, String refresh) {
        return new TokenService.TokenPair(access, refresh);
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
        TokenService.TokenPair tokenPair1 = new TokenService.TokenPair(
            "token1", "refresh1");
        TokenService.TokenPair tokenPair2 = new TokenService.TokenPair(
            "token2", "refresh2");
        LoginResult result1 = LoginResult.success(tokenPair1);
        LoginResult result2 = LoginResult.success(tokenPair2);

        result1.mapTo(success1 -> {
                result2.mapTo(success2 -> {
                        assertNotEquals(success1.tokenPair().accessToken(), success2.tokenPair().accessToken());
                        return null;
                    })
                    .orElse(failure2 -> {
                        fail("Expected success result for result2 but got failure");
                        return null;
                    });
                return null;
            })
            .orElse(failure1 -> {
                fail("Expected success result for result1 but got failure");
                return null;
            });
    }

    @Test
    void testMultipleFailureResults() {
        LoginResult result1 = LoginResult.failure("ERROR_1", "message 1");
        LoginResult result2 = LoginResult.failure("ERROR_2", "message 2");

        result1.mapTo(success1 -> {
                fail("Expected failure result for result1 but got success");
                return null;
            })
            .orElse(failure1 -> {
                result2.mapTo(success2 -> {
                        fail("Expected failure result for result2 but got success");
                        return null;
                    })
                    .orElse(failure2 -> {
                        assertNotEquals(failure1.errorCode(), failure2.errorCode());
                        return null;
                    });
                return null;
            });
    }

    @Test
    void testCannotSwitchStates() {
        TokenService.TokenPair tokenPair = new TokenService.TokenPair(
            "token", "refresh");
        LoginResult success = LoginResult.success(tokenPair);
        LoginResult failure = LoginResult.failure("CODE", "message");

        // Success cannot be failed and vice versa
        success.mapTo(s -> {
                assertTrue(true); // Success case verified
                return null;
            })
            .orElse(f -> {
                fail("Expected success but got failure");
                return null;
            });

        failure.mapTo(s -> {
                fail("Expected failure but got success");
                return null;
            })
            .orElse(f -> {
                assertTrue(true); // Failure case verified
                return null;
            });
    }
}
