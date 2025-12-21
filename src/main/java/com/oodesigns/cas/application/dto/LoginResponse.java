package com.oodesigns.cas.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Transport DTO for login response.
 * Shaped for REST API; includes permissions for UI and API access control.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class LoginResponse {
    private boolean success;
    private String accessToken;
    private String refreshToken;
    private List<String> permissions;
    private String errorCode;
    private String errorMessage;

    private LoginResponse() {
    }

    public static LoginResponse success(final String accessToken, final String refreshToken, final List<String> permissions) {
        LoginResponse dto = new LoginResponse();
        dto.success = true;
        dto.accessToken = accessToken;
        dto.refreshToken = refreshToken;
        dto.permissions = permissions;
        return dto;
    }

    public static LoginResponse failure(final String errorCode, final String errorMessage) {
        LoginResponse dto = new LoginResponse();
        dto.success = false;
        dto.errorCode = errorCode;
        dto.errorMessage = errorMessage;
        return dto;
    }

    // Getters
    public boolean isSuccess() {
        return success;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
