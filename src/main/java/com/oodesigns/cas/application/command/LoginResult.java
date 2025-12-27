package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.UserId;
import java.util.Set;
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

    static SuccessResult success(final TokenService.TokenPair tokenPair, final UserId userId, final Set<Permission> permissions) {
        return new SuccessResult(tokenPair, userId, permissions);
    }

    static FailureResult failure(final String errorCode, final String errorMessage) {
        return new FailureResult(errorCode, errorMessage);
    }

    /**
     * Successful login result containing token information and user metadata.
     * Permissions and userId are included so client can immediately use them
     * without needing to decode the JWT.
     */
    record SuccessResult(
        TokenService.TokenPair tokenPair,
        UserId userId,
        Set<Permission> permissions
    ) implements LoginResult {

        public SuccessResult {
            if (tokenPair == null) {
                throw new IllegalArgumentException("Token pair is required");
            }
            if (userId == null) {
                throw new IllegalArgumentException("User ID is required");
            }
            if (permissions == null) {
                throw new IllegalArgumentException("Permissions cannot be null");
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
