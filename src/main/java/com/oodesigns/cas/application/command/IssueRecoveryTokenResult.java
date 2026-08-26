package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.RecoveryToken;
import java.util.Objects;
import java.util.function.Function;

public sealed interface IssueRecoveryTokenResult permits IssueRecoveryTokenResult.Success, IssueRecoveryTokenResult.Failure {
    <T> T fold(Function<Success, T> onSuccess, Function<Failure, T> onFailure);

    static Success success(final RecoveryToken token) { return new Success(token); }
    static Failure failure(final String code, final String message) { return new Failure(code, message); }

    record Success(RecoveryToken token) implements IssueRecoveryTokenResult {
        public Success { Objects.requireNonNull(token, "Recovery token is required"); }
        public <T> T fold(final Function<Success, T> onSuccess, final Function<Failure, T> onFailure) { return onSuccess.apply(this); }
    }

    record Failure(String errorCode, String errorMessage) implements IssueRecoveryTokenResult {
        public Failure { Objects.requireNonNull(errorCode); Objects.requireNonNull(errorMessage); }
        public <T> T fold(final Function<Success, T> onSuccess, final Function<Failure, T> onFailure) { return onFailure.apply(this); }
    }
}