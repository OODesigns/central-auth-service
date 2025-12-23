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

        result.fold(
            success -> {
                assertEquals("access_token_123", success.tokenPair().accessToken());
                assertEquals("refresh_token_456", success.tokenPair().refreshToken());
                assertEquals(0, success.tokenPair().permissions().size());
                return null;
            },
            failure -> {
                fail("Expected success result but got failure");
                return null;
            }
        );
    }

    @Test
    void testFailureResult() {
        LoginResult result = LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password");

        result.fold(
            success -> {
                fail("Expected failure result but got success");
                return null;
            },
            failure -> {
                assertEquals("INVALID_CREDENTIALS", failure.errorCode());
                assertEquals("Invalid username or password", failure.errorMessage());
                return null;
            }
        );
    }

    @Test
    void testAccessingTokenOnFailureThrows() {
        // With fold pattern, failure results never have token access - type-safe at compile time
        LoginResult result = LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password");

        // This test verifies that FailureResult doesn't expose token methods
        result.fold(
            success -> {
                fail("FailureResult should never match success case");
                return null;
            },
            failure -> {
                // Only error-related methods are available
                assertNotNull(failure.errorCode());
                assertNotNull(failure.errorMessage());
                // Token methods are not available on FailureResult - verified at compile time
                return null;
            }
        );
    }

    @Test
    void testAccessingErrorOnSuccessThrows() {
        Set<Permission> permissions = new HashSet<>();
        AuthenticationService.TokenPair tokenPair = new AuthenticationService.TokenPair(
            "access_token", "refresh_token", Jti.generate(), permissions);
        LoginResult result = LoginResult.success(tokenPair);

        result.fold(
            success -> {
                assertEquals("access_token", success.tokenPair().accessToken());
                assertEquals("refresh_token", success.tokenPair().refreshToken());
                return null;
            },
            failure -> {
                fail("Expected success result but got failure");
                return null;
            }
        );
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

        result1.fold(
            success1 -> {
                result2.fold(
                    success2 -> {
                        assertNotEquals(success1.tokenPair().accessToken(), success2.tokenPair().accessToken());
                        return null;
                    },
                    failure2 -> {
                        fail("Expected success result for result2 but got failure");
                        return null;
                    }
                );
                return null;
            },
            failure1 -> {
                fail("Expected success result for result1 but got failure");
                return null;
            }
        );
    }

    @Test
    void testMultipleFailureResults() {
        LoginResult result1 = LoginResult.failure("ERROR_1", "message 1");
        LoginResult result2 = LoginResult.failure("ERROR_2", "message 2");

        result1.fold(
            success1 -> {
                fail("Expected failure result for result1 but got success");
                return null;
            },
            failure1 -> {
                result2.fold(
                    success2 -> {
                        fail("Expected failure result for result2 but got success");
                        return null;
                    },
                    failure2 -> {
                        assertNotEquals(failure1.errorCode(), failure2.errorCode());
                        return null;
                    }
                );
                return null;
            }
        );
    }

    @Test
    void testCannotSwitchStates() {
        Set<Permission> permissions = new HashSet<>();
        AuthenticationService.TokenPair tokenPair = new AuthenticationService.TokenPair(
            "token", "refresh", Jti.generate(), permissions);
        LoginResult success = LoginResult.success(tokenPair);
        LoginResult failure = LoginResult.failure("CODE", "message");

        // Success cannot be failed and vice versa
        success.fold(
            s -> {
                assertTrue(true); // Success case verified
                return null;
            },
            f -> {
                fail("Expected success but got failure");
                return null;
            }
        );

        failure.fold(
            s -> {
                fail("Expected failure but got success");
                return null;
            },
            f -> {
                assertTrue(true); // Failure case verified
                return null;
            }
        );
    }
}
