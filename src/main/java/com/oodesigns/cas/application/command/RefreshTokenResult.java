package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.UserId;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Result of a refresh-token rotation using the fluent mapTo(...).orElse(...) pattern.
 * <p>
 * Variants:
 * <ul>
 *   <li>{@link SuccessResult}: rotation succeeded; a fresh access + refresh token pair is issued.</li>
 *   <li>{@link FailureResult}: rotation failed. Possible error codes:
 *     <ul>
 *       <li>{@code INVALID_REFRESH_TOKEN} — token is missing, malformed, expired, has a bad
 *           signature/audience, or is unknown to the store.</li>
 *       <li>{@code REFRESH_TOKEN_REUSE_DETECTED} — an already-rotated token was replayed; the
 *           entire token family has been revoked and the user must log in again.</li>
 *       <li>{@code REFRESH_TOKEN_EXPIRED} — the stored token had already expired.</li>
 *       <li>{@code USER_NOT_FOUND} — the subject user no longer exists.</li>
 *       <li>{@code INTERNAL_ERROR} — infrastructure failure (e.g. token signing).</li>
 *       <li>{@code INVALID_REQUEST} — null command.</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public sealed interface RefreshTokenResult
    permits RefreshTokenResult.SuccessResult, RefreshTokenResult.FailureResult {

    <T> Mapper<T> mapTo(Function<SuccessResult, T> successMapper);

    static SuccessResult success(final TokenService.TokenPair tokenPair,
                                 final UserId userId,
                                 final Set<Permission> permissions) {
        return new SuccessResult(tokenPair, userId, permissions);
    }

    static FailureResult failure(final String errorCode, final String errorMessage) {
        return new FailureResult(errorCode, errorMessage);
    }

    /**
     * Rotation succeeded; a fresh access + refresh token pair is ready for the client.
     * Mirrors {@link LoginResult.SuccessResult} so the delivery layer can handle all
     * token-issuance paths uniformly.
     */
    record SuccessResult(TokenService.TokenPair tokenPair, UserId userId,
                         Set<Permission> permissions) implements RefreshTokenResult {
        public SuccessResult {
            Objects.requireNonNull(tokenPair, "TokenPair is required");
            Objects.requireNonNull(userId, "UserId is required");
            Objects.requireNonNull(permissions, "Permissions are required");
            permissions = Set.copyOf(permissions);
        }

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

    /**
     * Refresh-token rotation failed.
     */
    record FailureResult(String errorCode, String errorMessage) implements RefreshTokenResult {
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

