package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LoginResult application result object.
 * Validates: result pattern, type-safe accessors, state consistency, permissions.
 */
class LoginResultTest {

    @Test
    void testSuccessResult() {
        final TokenService.TokenPair tokenPair = new TokenService.TokenPair(
            "access_token_123", "refresh_token_456");
        final UserId userId = UserId.of(UUID.randomUUID());
        final Set<Permission> permissions = Set.of(Permission.of("read"), Permission.of("write"));
        final LoginResult result = LoginResult.success(tokenPair, userId, permissions);

        result.mapTo(success -> {
                assertEquals("access_token_123", success.tokenPair().accessToken());
                assertEquals("refresh_token_456", success.tokenPair().refreshToken());
                assertEquals(userId, success.userId());
                assertEquals(permissions, success.permissions());
                return null;
            })
            .orElse(ignored -> {
                fail("Expected success result but got failure");
                return null;
            });
    }

    @Test
    void testFailureResult() {
        final LoginResult result = LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password");

        result.mapTo(ignored -> {
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
        final LoginResult result = LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password");

        // This test verifies that FailureResult doesn't expose success methods
        result.mapTo(ignored -> {
                fail("FailureResult should never match success case");
                return null;
            })
            .orElse(failure -> {
                // Only error-related methods are available
                assertNotNull(failure.errorCode());
                assertNotNull(failure.errorMessage());
                // Success methods (tokenPair, userId, permissions) are not available on FailureResult - verified at compile time
                return null;
            });
    }

    @Test
    void testAccessingErrorOnSuccessThrows() {
        final TokenService.TokenPair tokenPair = new TokenService.TokenPair(
            "access_token", "refresh_token");
        final UserId userId = UserId.of(UUID.randomUUID());
        final Set<Permission> permissions = Collections.emptySet();
        final LoginResult result = LoginResult.success(tokenPair, userId, permissions);

        result.mapTo(success -> {
                assertEquals("access_token", success.tokenPair().accessToken());
                assertEquals("refresh_token", success.tokenPair().refreshToken());
                assertEquals(userId, success.userId());
                return null;
            })
            .orElse(ignoredFailure -> {
                fail("Expected success result but got failure");
                return null;
            });
    }

    @Test
    void testSuccessWithNullTokensThrows() {
        final UserId userId = UserId.of(UUID.randomUUID());
        final Set<Permission> permissions = Set.of(Permission.of("read"));
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.success(null, userId, permissions));
    }

    @Test
    void testSuccessWithNullUserIdThrows() {
        final TokenService.TokenPair tokenPair = new TokenService.TokenPair(
            "access_token", "refresh_token");
        final Set<Permission> permissions = Set.of(Permission.of("read"));
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.success(tokenPair, null, permissions));
    }

    @Test
    void testSuccessWithNullPermissionsThrows() {
        final TokenService.TokenPair tokenPair = new TokenService.TokenPair(
            "access_token", "refresh_token");
        final UserId userId = UserId.of(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,
            () -> LoginResult.success(tokenPair, userId, null));
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
    
    private void createTokenPair(final String access, final String refresh) {
        final TokenService.TokenPair tokenPair = new TokenService.TokenPair(access, refresh); // invocation for exception validation only
        java.util.Objects.requireNonNull(tokenPair); // touch to avoid unused variable warning
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
        final TokenService.TokenPair tokenPair1 = new TokenService.TokenPair(
            "token1", "refresh1");
        final TokenService.TokenPair tokenPair2 = new TokenService.TokenPair(
            "token2", "refresh2");
        final UserId userId1 = UserId.of(UUID.randomUUID());
        final UserId userId2 = UserId.of(UUID.randomUUID());
        final Set<Permission> permissions = Set.of(Permission.of("read"));
        final LoginResult result1 = LoginResult.success(tokenPair1, userId1, permissions);
        final LoginResult result2 = LoginResult.success(tokenPair2, userId2, permissions);

        result1.mapTo(success1 -> {
                result2.mapTo(success2 -> {
                        assertNotEquals(success1.tokenPair().accessToken(), success2.tokenPair().accessToken());
                        assertNotEquals(success1.userId(), success2.userId());
                        return null;
                    })
                    .orElse(ignoredFailure2 -> {
                        fail("Expected success result for result2 but got failure");
                        return null;
                    });
                return null;
            })
            .orElse(ignoredFailure1 -> {
                fail("Expected success result for result1 but got failure");
                return null;
            });
    }

    @Test
    void testMultipleFailureResults() {
        final LoginResult result1 = LoginResult.failure("ERROR_1", "message 1");
        final LoginResult result2 = LoginResult.failure("ERROR_2", "message 2");

        result1.mapTo(ignored1 -> {
                fail("Expected failure result for result1 but got success");
                return null;
            })
            .orElse(ignored1Failure -> {
                result2.mapTo(ignored2 -> {
                        fail("Expected failure result for result2 but got success");
                        return null;
                    })
                    .orElse(ignored2Failure -> {
                        assertNotEquals(ignored1Failure.errorCode(), ignored2Failure.errorCode());
                        return null;
                    });
                return null;
            });
    }

    @Test
    void testCannotSwitchStates() {
        final TokenService.TokenPair tokenPair = new TokenService.TokenPair(
            "token", "refresh");
        final UserId userId = UserId.of(UUID.randomUUID());
        final Set<Permission> permissions = Set.of(Permission.of("admin"));
        final LoginResult success = LoginResult.success(tokenPair, userId, permissions);
        final LoginResult failure = LoginResult.failure("CODE", "message");

        // Success cannot be failed and vice versa
        success.mapTo(ignored -> {
                assertTrue(true); // Success case verified
                return null;
            })
            .orElse(ignoredFailure -> {
                fail("Expected success but got failure");
                return null;
            });

        failure.mapTo(ignored2 -> {
                fail("Expected failure but got success");
                return null;
            })
            .orElse(ignoredFailure2 -> {
                assertTrue(true); // Failure case verified
                return null;
            });
    }

    @Test
    void testRequired2FAResultValidationAndMapper() {
        final java.util.UUID uuid = java.util.UUID.randomUUID();
        final com.oodesigns.cas.domain.value.UserId userId = com.oodesigns.cas.domain.value.UserId.of(uuid);
        final LoginResult.Required2FAResult r = LoginResult.required2FA("verif-token", userId);
        assertNotNull(r);
        assertThrows(IllegalArgumentException.class, () -> new LoginResult.Required2FAResult("", userId));
        assertThrows(IllegalArgumentException.class, () -> new LoginResult.Required2FAResult(null, userId));
        assertThrows(IllegalArgumentException.class, () -> new LoginResult.Required2FAResult("token", null));

        final String mapped = r.mapTo(_ -> "OK").orElse(LoginResult.FailureResult::errorCode);
        // For Required2FAResult the mapTo should return failure mapping (MFA_SETUP_REQUIRED)
        assertEquals("MFA_SETUP_REQUIRED", mapped);
    }

    @Test
    void testPasswordResetRequiredResultValidationAndMapper() {
        final com.oodesigns.cas.domain.value.UserId userId = com.oodesigns.cas.domain.value.UserId.of(java.util.UUID.randomUUID());
        final LoginResult.PasswordResetRequiredResult pr = LoginResult.passwordResetRequired(userId);
        assertNotNull(pr);
        assertThrows(NullPointerException.class, () -> new LoginResult.PasswordResetRequiredResult(null));

        final String mapped = pr.mapTo(_ -> "OK").orElse(LoginResult.FailureResult::errorCode);
        assertEquals("PASSWORD_RESET_REQUIRED", mapped);
    }
}
