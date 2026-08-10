package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.UserId;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Result of 2FA verification using the fluent mapTo(...).orElse(...) pattern.
 * <p>
 * Variants:
 * <ul>
 *   <li>{@link SuccessResult}: 2FA verified; full access + refresh tokens issued.</li>
 *   <li>{@link FailureResult}: verification failed. Possible error codes:
 *     <ul>
 *       <li>{@code INVALID_VERIFICATION_TOKEN} — the 2FA token is expired, has a bad
 *           signature, or has the wrong audience.</li>
 *       <li>{@code INVALID_TOTP_CODE} — the OTP or backup code did not match.</li>
 *       <li>{@code USER_NOT_FOUND} — user ID from the token no longer exists.</li>
 *       <li>{@code INTERNAL_ERROR} — infrastructure failure.</li>
 *       <li>{@code INVALID_REQUEST} — null command.</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public sealed interface VerifyTotpResult
    permits VerifyTotpResult.SuccessResult, VerifyTotpResult.FailureResult {

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
     * 2FA verified; full access + refresh tokens are ready for the client.
     * Mirrors the shape of {@link LoginResult.SuccessResult} so the delivery layer
     * can handle both token-issuance paths uniformly.
     */
    record SuccessResult(TokenService.TokenPair tokenPair, UserId userId,
                         Set<Permission> permissions) implements VerifyTotpResult {
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
     * 2FA verification failed.
     */
    record FailureResult(String errorCode, String errorMessage) implements VerifyTotpResult {
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

