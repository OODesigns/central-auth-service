package com.oodesigns.cas.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Transport DTO for login response.
 * Shaped for REST API; includes permissions for UI and API access control.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface LoginResponse {
    boolean isSuccess();

    String getAccessToken();

    String getRefreshToken();

    List<String> getPermissions();

    String getErrorCode();

    String getErrorMessage();

    static LoginResponse success(final String accessToken, final String refreshToken, final List<String> permissions) {
        return new SuccessResponse(true, accessToken, refreshToken, permissions, null, null);
    }

    static LoginResponse failure(final String errorCode, final String errorMessage) {
        return new FailureResponse(false, null, null, null, errorCode, errorMessage);
    }

    /**
     * Successful login response.
     */
    record SuccessResponse(boolean success, String accessToken, String refreshToken, List<String> permissions,
                           String errorCode, String errorMessage) implements LoginResponse {
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
        public List<String> getPermissions() {
            return permissions;
        }

        @Override
        public String getErrorCode() {
            return null;
        }

        @Override
        public String getErrorMessage() {
            return null;
        }
    }

    /**
     * Failed login response.
     */
    record FailureResponse(boolean success, String accessToken, String refreshToken, List<String> permissions,
                           String errorCode, String errorMessage) implements LoginResponse {
        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public String getAccessToken() {
            return null;
        }

        @Override
        public String getRefreshToken() {
            return null;
        }

        @Override
        public List<String> getPermissions() {
            return null;
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
