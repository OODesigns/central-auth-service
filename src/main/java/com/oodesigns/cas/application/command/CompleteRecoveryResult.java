package com.oodesigns.cas.application.command;

import java.util.Objects;
import java.util.function.Function;

public sealed interface CompleteRecoveryResult permits CompleteRecoveryResult.Success, CompleteRecoveryResult.Failure {
    <T> T fold(Function<Success, T> onSuccess, Function<Failure, T> onFailure);
    static Success success() { return new Success(); }
    static Failure failure(final String code, final String message) { return new Failure(code, message); }

    record Success() implements CompleteRecoveryResult {
        public <T> T fold(final Function<Success, T> onSuccess, final Function<Failure, T> onFailure) { return onSuccess.apply(this); }
    }
    record Failure(String errorCode, String errorMessage) implements CompleteRecoveryResult {
        public Failure { Objects.requireNonNull(errorCode); Objects.requireNonNull(errorMessage); }
        public <T> T fold(final Function<Success, T> onSuccess, final Function<Failure, T> onFailure) { return onFailure.apply(this); }
    }
}