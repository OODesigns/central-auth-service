package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.AuthenticationService;
import java.util.function.Function;

/**
 * Result of login command execution using the fold pattern.
 * Clients apply lambdas to handle success or failure cases.
 * No type-checking needed - type-safe at compile time.
 */
public sealed interface LoginResult {
    /**
     * Applies different functions based on success or failure.
     * This is the fold/visitor pattern - clients provide handlers for each case.
     * 
     * @param onSuccess function to call if login succeeded, receives SuccessResult
     * @param onFailure function to call if login failed, receives FailureResult
     * @return the result of the applied function
     * @param <T> return type
     */
    <T> T fold(
        Function<SuccessResult, T> onSuccess,
        Function<FailureResult, T> onFailure
    );

    static SuccessResult success(final AuthenticationService.TokenPair tokenPair) {
        return new SuccessResult(tokenPair);
    }

    static FailureResult failure(final String errorCode, final String errorMessage) {
        return new FailureResult(errorCode, errorMessage);
    }

    /**
     * Successful login result containing token information.
     */
    record SuccessResult(AuthenticationService.TokenPair tokenPair) implements LoginResult {
        public SuccessResult {
            if (tokenPair == null) {
                throw new IllegalArgumentException("Token pair is required");
            }
        }

        @Override
        public <T> T fold(
            Function<SuccessResult, T> onSuccess,
            Function<FailureResult, T> onFailure
        ) {
            return onSuccess.apply(this);
        }
    }

    /**
     * Failed login result containing error details.
     */
    record FailureResult(String errorCode, String errorMessage) implements LoginResult {
        public FailureResult {
            if (errorCode == null || errorCode.isBlank()) {
                throw new IllegalArgumentException("Error code is required");
            }
            if (errorMessage == null || errorMessage.isBlank()) {
                throw new IllegalArgumentException("Error message is required");
            }
        }

        @Override
        public <T> T fold(
            Function<SuccessResult, T> onSuccess,
            Function<FailureResult, T> onFailure
        ) {
            return onFailure.apply(this);
        }
    }
}
