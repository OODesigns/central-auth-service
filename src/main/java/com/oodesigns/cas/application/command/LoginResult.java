package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.Permission;
import java.util.Collections;
import java.util.Set;

/**
 * Result of login command execution.
 */
public final class LoginResult {
    private final boolean success;
    private final String accessToken;
    private final String refreshToken;
    private final Set<Permission> permissions;
    private final String errorCode;
    private final String errorMessage;

    private LoginResult(final boolean success, final String accessToken, final String refreshToken, final Set<Permission> permissions,
                       final String errorCode, final String errorMessage) {
        this.success = success;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.permissions = permissions;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static LoginResult success(final String accessToken, final String refreshToken, final Set<Permission> permissions) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token is required");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }
        if (permissions == null) {
            throw new IllegalArgumentException("Permissions cannot be null");
        }
        return new LoginResult(true, accessToken, refreshToken, Collections.unmodifiableSet(permissions), null, null);
    }

    public static LoginResult failure(final String errorCode, final String errorMessage) {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("Error code is required");
        }
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("Error message is required");
        }
        return new LoginResult(false, null, null, Collections.emptySet(), errorCode, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getAccessToken() {
        if (!success) throw new IllegalStateException("Login failed");
        return accessToken;
    }

    public String getRefreshToken() {
        if (!success) throw new IllegalStateException("Login failed");
        return refreshToken;
    }

    public Set<Permission> getPermissions() {
        if (!success) throw new IllegalStateException("Login failed");
        return permissions;
    }

    public String getErrorCode() {
        if (success) throw new IllegalStateException("Login succeeded");
        return errorCode;
    }

    public String getErrorMessage() {
        if (success) throw new IllegalStateException("Login succeeded");
        return errorMessage;
    }
}
