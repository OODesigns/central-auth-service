package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.AuthenticationService;
import java.util.function.Function;

/**
 * Result of login command execution using a fluent mapTo(...).orElse(...) pattern.
 * Implementations provide their own mapping without runtime type checks.
 */
public sealed interface LoginResult
    permits LoginResult.SuccessResult, LoginResult.FailureResult {

    /**
     * Fluent mapping API: map success, then provide fallback for failure.
     * Usage:
     * <pre>
     * LoginResponseDTO dto = result
     *     .mapTo(success -> ...)
     *     .orElse(failure -> ...);
     * </pre>
     */
    <T> Mapper<T> mapTo(Function<SuccessResult, T> successMapper);

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
        public <T> Mapper<T> mapTo(Function<SuccessResult, T> successMapper) {
            return new MapperSuccess<>(successMapper.apply(this));
        }

        /**
         * Mapper returned when the result is a success.
         */
        static final class MapperSuccess<T> implements Mapper<T> {
            private final T value;

            MapperSuccess(T value) {
                this.value = value;
            }

            @Override
            public T orElse(Function<FailureResult, T> failureMapper) {
                return value;
            }
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
        public <T> Mapper<T> mapTo(Function<SuccessResult, T> successMapper) {
            // Success mapper intentionally ignored for failures
            return new MapperFailure<>(this);
        }

        /**
         * Mapper returned when the result is a failure.
         */
        static final class MapperFailure<T> implements Mapper<T> {
            private final FailureResult failure;

            MapperFailure(FailureResult failure) {
                this.failure = failure;
            }

            @Override
            public T orElse(Function<FailureResult, T> failureMapper) {
                return failureMapper.apply(failure);
            }
        }
    }

    /**
     * Common mapper contract for the fluent API.
     */
    interface Mapper<T> {
        T orElse(Function<FailureResult, T> failureMapper);
    }

}
