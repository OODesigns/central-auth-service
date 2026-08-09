package com.oodesigns.cas.application.command;

import java.util.Objects;
import java.util.function.Function;

/**
 * Result of TOTP setup initiation using the fluent mapTo(...).orElse(...) pattern.
 * <p>
 * Variants:
 * <ul>
 *   <li>{@link SuccessResult}: secret generated and persisted; caller should display the
 *       {@code otpauth://} URI (e.g. as a QR code) so the user can enrol their device.</li>
 *   <li>{@link FailureResult}: setup failed (e.g. infrastructure error).</li>
 * </ul>
 */
public sealed interface SetupTotpResult
    permits SetupTotpResult.SuccessResult, SetupTotpResult.FailureResult {

    <T> Mapper<T> mapTo(Function<SuccessResult, T> successMapper);

    static SuccessResult success(final String secret, final String otpauthUri) {
        return new SuccessResult(secret, otpauthUri);
    }

    static FailureResult failure(final String errorCode, final String errorMessage) {
        return new FailureResult(errorCode, errorMessage);
    }

    /**
     * TOTP setup initiated successfully.
     * <p>
     * {@code secret} is the Base32-encoded TOTP secret — display it to the user exactly
     * once (as a manual-entry fallback if they cannot scan the QR code). Never log it or
     * transmit it more than once.
     * <p>
     * {@code otpauthUri} is the standard {@code otpauth://totp/...} URI that encodes the
     * secret, issuer, and account name for QR-code generation.
     */
    record SuccessResult(String secret, String otpauthUri) implements SetupTotpResult {
        public SuccessResult {
            Objects.requireNonNull(secret, "Secret is required");
            Objects.requireNonNull(otpauthUri, "otpauthUri is required");
            if (secret.isBlank()) {
                throw new IllegalArgumentException("Secret cannot be blank");
            }
            if (!otpauthUri.startsWith("otpauth://totp/")) {
                throw new IllegalArgumentException("otpauthUri must start with 'otpauth://totp/'");
            }
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
     * TOTP setup failed.
     * <p>
     * Possible error codes:
     * <ul>
     *   <li>{@code INTERNAL_ERROR} — infrastructure error during secret generation.</li>
     *   <li>{@code INVALID_REQUEST} — null or otherwise invalid command.</li>
     * </ul>
     */
    record FailureResult(String errorCode, String errorMessage) implements SetupTotpResult {
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

