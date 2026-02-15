package com.oodesigns.cas.application.command;
import java.util.function.Function;

/**
 * Result of disable TOTP command execution using fluent mapTo(...).orElse(...) pattern.
 * <p>
 * Variants:
 * - SuccessResult: TOTP successfully disabled, backup codes wiped
 * - FailureResult: Disable failed (invalid password, not enrolled, etc.)
 */
public sealed interface DisableTotpResult
    permits DisableTotpResult.SuccessResult, DisableTotpResult.FailureResult {

    /**
     * Fluent mapping API: map success, then provide fallback for failure.
     */
    <T> Mapper<T> mapTo(Function<SuccessResult, T> successMapper);

    static SuccessResult success() {
        return new SuccessResult();
    }

    static FailureResult failure(final String errorCode, final String errorMessage) {
        return new FailureResult(errorCode, errorMessage);
    }

    /**
     * TOTP successfully disabled.
     * - User's TOTP secret has been deleted
     * - All backup codes have been wiped
     * - Audit event 'TOTP_DISABLED' has been logged
     * - User can re-enroll 2FA anytime
     */
    record SuccessResult() implements DisableTotpResult {
        @Override
        public <T> Mapper<T> mapTo(final Function<SuccessResult, T> successMapper) {
            return new MapperSuccess<>(successMapper.apply(this));
        }

        /**
         * Mapper returned when TOTP is successfully disabled.
         */
        static final class MapperSuccess<T> implements Mapper<T> {
            private final T value;

            MapperSuccess(final T value) {
                this.value = value;
            }

            @Override
            public T orElse(final Function<FailureResult, T> failureMapper) {
                return value;
            }
        }
    }

    /**
     * Failed to disable TOTP containing error details.
     * Possible error codes:
     * - "INVALID_PASSWORD": Password verification failed (requires re-auth)
     * - "TOTP_NOT_ENABLED": User doesn't have 2FA enabled
     * - "INTERNAL_ERROR": Database or service error
     */
    record FailureResult(String errorCode, String errorMessage) implements DisableTotpResult {
        public FailureResult {
            if (errorCode == null || errorCode.isBlank()) {
                throw new IllegalArgumentException("Error code is required");
            }
            if (errorMessage == null || errorMessage.isBlank()) {
                throw new IllegalArgumentException("Error message is required");
            }
        }

        @Override
        public <T> Mapper<T> mapTo(final Function<SuccessResult, T> successMapper) {
            // Success mapper intentionally ignored for failures
            return new MapperFailure<>(this);
        }

        /**
         * Mapper returned when TOTP disable fails.
         */
        static final class MapperFailure<T> implements Mapper<T> {
            private final FailureResult failure;

            MapperFailure(final FailureResult failure) {
                this.failure = failure;
            }

            @Override
            public T orElse(final Function<FailureResult, T> failureMapper) {
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

