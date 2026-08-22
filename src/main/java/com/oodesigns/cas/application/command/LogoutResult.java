package com.oodesigns.cas.application.command;

import java.util.function.Function;

/**
 * Result of logout command execution using the fluent mapTo(...).orElse(...) pattern.
 */
public sealed interface LogoutResult permits LogoutResult.SuccessResult, LogoutResult.FailureResult {

    <T> Mapper<T> mapTo(Function<SuccessResult, T> successMapper);

    static SuccessResult success() {
        return new SuccessResult();
    }

    static FailureResult failure(final String errorCode, final String errorMessage) {
        return new FailureResult(errorCode, errorMessage);
    }

    record SuccessResult() implements LogoutResult {
        @Override
        public <T> Mapper<T> mapTo(final Function<SuccessResult, T> successMapper) {
            return new MapperSuccess<>(successMapper.apply(this));
        }

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

    record FailureResult(String errorCode, String errorMessage) implements LogoutResult {
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
            return new MapperFailure<>(this);
        }

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

    interface Mapper<T> {
        T orElse(Function<FailureResult, T> failureMapper);
    }
}