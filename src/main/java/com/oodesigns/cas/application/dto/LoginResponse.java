package com.oodesigns.cas.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.List;

/**
 * Transport DTO for login response.
 * Sealed interface hierarchy providing type-safe success/failure variants.
 * Shaped for REST API and gRPC serialization.
 * Uses @JsonInclude to omit null fields from JSON output.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface LoginResponse {
    boolean isSuccess();

    static LoginResponse success(final String accessToken, final String refreshToken, final List<String> permissions) {
        return new SuccessResponse(accessToken, refreshToken, 
                                  permissions != null ? Collections.unmodifiableList(permissions) : Collections.emptyList());
    }

    static LoginResponse failure(final String errorCode, final String errorMessage) {
        return new FailureResponse(errorCode, errorMessage);
    }

    /**
     * Successful login response - contains only relevant fields.
     * Reduces serialization overhead and prevents null handling confusion.
     */
    record SuccessResponse(String accessToken, String refreshToken, List<String> permissions) implements LoginResponse {
        public SuccessResponse {
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalArgumentException("accessToken cannot be null or blank");
            }
            if (refreshToken == null || refreshToken.isBlank()) {
                throw new IllegalArgumentException("refreshToken cannot be null or blank");
            }
            if (permissions == null) {
                throw new IllegalArgumentException("permissions cannot be null");
            }
        }

        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    /**
     * Failed login response - contains only error information.
     * Reduces serialization overhead and prevents unnecessary token fields in error responses.
     */
    record FailureResponse(String errorCode, String errorMessage) implements LoginResponse {
        public FailureResponse {
            if (errorCode == null || errorCode.isBlank()) {
                throw new IllegalArgumentException("errorCode cannot be null or blank");
            }
            if (errorMessage == null || errorMessage.isBlank()) {
                throw new IllegalArgumentException("errorMessage cannot be null or blank");
            }
        }

        @Override
        public boolean isSuccess() {
            return false;
        }
    }
}
