package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.BackupCode;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Result of TOTP enable using the fluent mapTo(...).orElse(...) pattern.
 * <p>
 * Variants:
 * <ul>
 *   <li>{@link SuccessResult}: TOTP is now active. The {@code backupCodes} list must be
 *       displayed to the user exactly once and never stored in plaintext by the delivery
 *       layer. The underlying adapter has already stored BCrypt-hashed versions.</li>
 *   <li>{@link FailureResult}: Enable failed. Possible error codes:
 *     <ul>
 *       <li>{@code INVALID_TOTP_CODE} — the submitted OTP code did not match.</li>
 *       <li>{@code TOTP_ALREADY_ENABLED} — TOTP was already active for this user.</li>
 *       <li>{@code INTERNAL_ERROR} — infrastructure failure.</li>
 *       <li>{@code INVALID_REQUEST} — null command.</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public sealed interface EnableTotpResult
    permits EnableTotpResult.SuccessResult, EnableTotpResult.FailureResult {

    <T> Mapper<T> mapTo(Function<SuccessResult, T> successMapper);

    static SuccessResult success(final List<BackupCode> backupCodes) {
        return new SuccessResult(backupCodes);
    }

    static FailureResult failure(final String errorCode, final String errorMessage) {
        return new FailureResult(errorCode, errorMessage);
    }

    /**
     * TOTP is now enabled. Backup codes are one-time visible — the delivery layer must
     * display them (via {@link BackupCode#getCode()}) immediately and never re-expose them.
     */
    record SuccessResult(List<BackupCode> backupCodes) implements EnableTotpResult {
        public SuccessResult {
            Objects.requireNonNull(backupCodes, "Backup codes list is required");
            if (backupCodes.isEmpty()) {
                throw new IllegalArgumentException("At least one backup code is required");
            }
            // Defensive copy — caller cannot mutate the list after construction
            backupCodes = List.copyOf(backupCodes);
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
     * TOTP enable failed.
     */
    record FailureResult(String errorCode, String errorMessage) implements EnableTotpResult {
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

