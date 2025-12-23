package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.value.Permission;
import java.util.Set;

/**
 * Result of login command execution.
 */
public sealed interface LoginResult {
    boolean isSuccess();

    String getAccessToken();

    String getRefreshToken();

    Set<Permission> getPermissions();

    String getErrorCode();

    String getErrorMessage();

    static LoginResult success(final AuthenticationService.TokenPair tokenPair) {
        if (tokenPair == null) {
            throw new IllegalArgumentException("Token pair is required");
        }
        return new SuccessResult(tokenPair);
    }

    static LoginResult failure(final String errorCode, final String errorMessage) {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("Error code is required");
        }
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("Error message is required");
        }
        return new FailureResult(errorCode, errorMessage);
    }

    /**
     * Successful login result.
     */
    record SuccessResult(AuthenticationService.TokenPair tokenPair) implements LoginResult {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public String getAccessToken() {
            return tokenPair.accessToken();
        }

        @Override
        public String getRefreshToken() {
            return tokenPair.refreshToken();
        }

        @Override
        public Set<Permission> getPermissions() {
            return tokenPair.permissions();
        }

        @Override
        public String getErrorCode() {
            throw new IllegalStateException("Login succeeded");
        }

        @Override
        public String getErrorMessage() {
            throw new IllegalStateException("Login succeeded");
        }
    }

    /**
     * Failed login result.
     */
    record FailureResult(String errorCode, String errorMessage) implements LoginResult {
        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public String getAccessToken() {
            throw new IllegalStateException("Login failed");
        }

        @Override
        public String getRefreshToken() {
            throw new IllegalStateException("Login failed");
        }

        @Override
        public Set<Permission> getPermissions() {
            throw new IllegalStateException("Login failed");
        }

        @Override
        public String getErrorCode() {
            return errorCode;
        }

        @Override
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
