package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.Permission;
import java.util.Collections;
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

    static LoginResult success(final String accessToken, final String refreshToken, final Set<Permission> permissions) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token is required");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }
        if (permissions == null) {
            throw new IllegalArgumentException("Permissions cannot be null");
        }
        return new SuccessResult(accessToken, refreshToken, Collections.unmodifiableSet(permissions));
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
    record SuccessResult(String accessToken, String refreshToken, Set<Permission> permissions) implements LoginResult {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public String getAccessToken() {
            return accessToken;
        }

        @Override
        public String getRefreshToken() {
            return refreshToken;
        }

        @Override
        public Set<Permission> getPermissions() {
            return permissions;
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
