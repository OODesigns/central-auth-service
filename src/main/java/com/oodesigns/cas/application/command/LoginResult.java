package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.UserId;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Result of login command execution using a fluent mapTo(...).orElse(...) pattern.
 * Implementations provide their own mapping without runtime type checks.
 * <p>
 * Variants:
 * - SuccessResult: Full authentication complete, tokens issued
 * - Required2FAResult: Password verified, 2FA verification required
 * - PasswordResetRequiredResult: Password reset is mandatory
 * - FailureResult: Authentication failed
 */
public sealed interface LoginResult
    permits LoginResult.SuccessResult, LoginResult.Required2FAResult,
            LoginResult.PasswordResetRequiredResult, LoginResult.MfaEnrollmentRequiredResult,
            LoginResult.FailureResult {

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

    <T> T fold(Function<SuccessResult, T> successMapper,
               Function<Required2FAResult, T> required2FAMapper,
               Function<PasswordResetRequiredResult, T> passwordResetMapper,
               Function<MfaEnrollmentRequiredResult, T> mfaEnrollmentMapper,
               Function<FailureResult, T> failureMapper);

    static SuccessResult success(final TokenService.TokenPair tokenPair, final UserId userId, final Set<Permission> permissions) {
        return new SuccessResult(tokenPair, userId, permissions);
    }

    static Required2FAResult required2FA(final String verificationToken, final UserId userId) {
        return new Required2FAResult(verificationToken, userId);
    }

    static PasswordResetRequiredResult passwordResetRequired(final UserId userId) {
        return new PasswordResetRequiredResult(userId);
    }

    static MfaEnrollmentRequiredResult mfaEnrollmentRequired(final String enrollmentToken, final UserId userId) {
        return new MfaEnrollmentRequiredResult(enrollmentToken, userId);
    }

    static FailureResult failure(final String errorCode, final String errorMessage) {
        return new FailureResult(errorCode, errorMessage);
    }

    /**
     * Successful login result containing token information and user metadata.
     * Permissions and userId are included so client can immediately use them
     * without needing to decode the JWT.
     */
    record SuccessResult(TokenService.TokenPair tokenPair, UserId userId, Set<Permission> permissions) implements LoginResult {

        public SuccessResult {
            if (tokenPair == null) {
                throw new IllegalArgumentException("TokenPair cannot be null");
            }
            if (userId == null) {
                throw new IllegalArgumentException("User ID is required");
            }
            if (permissions == null) {
                throw new IllegalArgumentException("Permissions cannot be null");
            }
        }

        @Override
        public <T> Mapper<T> mapTo(final Function<SuccessResult, T> successMapper) {
            return new MapperSuccess<>(successMapper.apply(this));
        }

        @Override
        public <T> T fold(final Function<SuccessResult, T> successMapper,
                          final Function<Required2FAResult, T> required2FAMapper,
                          final Function<PasswordResetRequiredResult, T> passwordResetMapper,
                          final Function<MfaEnrollmentRequiredResult, T> mfaEnrollmentMapper,
                          final Function<FailureResult, T> failureMapper) {
            return successMapper.apply(this);
        }

        /**
         * Mapper returned when the result is a success.
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
     * 2FA verification required result.
     * Returned when password is verified but user has 2FA enabled.
     * Client must exchange the restricted verificationToken for full tokens
     * by calling the /auth/verify-2fa endpoint with a valid 2FA code.
     */
    record Required2FAResult(String verificationToken, UserId userId) implements LoginResult {

        public Required2FAResult {
            if (verificationToken == null || verificationToken.isBlank()) {
                throw new IllegalArgumentException("2FA verification token is required");
            }
            if (userId == null) {
                throw new IllegalArgumentException("User ID is required");
            }
        }

        @Override
        public <T> Mapper<T> mapTo(final Function<SuccessResult, T> successMapper) {
            return new Mapper2FARequired<>(this);
        }

        @Override
        public <T> T fold(final Function<SuccessResult, T> successMapper,
                          final Function<Required2FAResult, T> required2FAMapper,
                          final Function<PasswordResetRequiredResult, T> passwordResetMapper,
                          final Function<MfaEnrollmentRequiredResult, T> mfaEnrollmentMapper,
                          final Function<FailureResult, T> failureMapper) {
            return required2FAMapper.apply(this);
        }

        /**
         * Mapper returned when 2FA verification is required.
         */
        static final class Mapper2FARequired<T> implements Mapper<T> {
            Mapper2FARequired(final Required2FAResult required2FA) {
                Objects.requireNonNull(required2FA, "Required2FAResult is required");
            }

            @Override
            public T orElse(final Function<FailureResult, T> failureMapper) {
                return failureMapper.apply(new FailureResult("MFA_SETUP_REQUIRED",
                    "MFA enrollment is required. Call SetupTotp to enroll."));
            }
        }
    }

    /**
     * Password reset is required before user can proceed with login.
     * Password-reset transport is outside the current AuthService gRPC contract.
     */
    record PasswordResetRequiredResult(UserId userId) implements LoginResult {

        public PasswordResetRequiredResult {
            Objects.requireNonNull(userId, "User ID is required");
        }

        @Override
        public <T> Mapper<T> mapTo(final Function<SuccessResult, T> successMapper) {
            return new MapperPasswordResetRequired<>(this);
        }

        @Override
        public <T> T fold(final Function<SuccessResult, T> successMapper,
                          final Function<Required2FAResult, T> required2FAMapper,
                          final Function<PasswordResetRequiredResult, T> passwordResetMapper,
                          final Function<MfaEnrollmentRequiredResult, T> mfaEnrollmentMapper,
                          final Function<FailureResult, T> failureMapper) {
            return passwordResetMapper.apply(this);
        }

        /**
         * Mapper returned when password reset is required.
         */
        static final class MapperPasswordResetRequired<T> implements Mapper<T> {
            MapperPasswordResetRequired(final PasswordResetRequiredResult result) {
                Objects.requireNonNull(result, "PasswordResetRequiredResult is required");
            }

            @Override
            public T orElse(final Function<FailureResult, T> failureMapper) {
                return failureMapper.apply(new FailureResult("PASSWORD_RESET_REQUIRED",
                    "Password reset is mandatory. Use /auth/reset-password endpoint."));
            }
        }
    }

    record MfaEnrollmentRequiredResult(String enrollmentToken, UserId userId) implements LoginResult {
        public MfaEnrollmentRequiredResult {
            if (enrollmentToken == null || enrollmentToken.isBlank()) {
                throw new IllegalArgumentException("MFA enrollment token is required");
            }
            Objects.requireNonNull(userId, "User ID is required");
        }

        @Override
        public <T> Mapper<T> mapTo(final Function<SuccessResult, T> successMapper) {
            return new MapperMfaEnrollmentRequired<>(this);
        }

        @Override
        public <T> T fold(final Function<SuccessResult, T> successMapper,
                          final Function<Required2FAResult, T> required2FAMapper,
                          final Function<PasswordResetRequiredResult, T> passwordResetMapper,
                          final Function<MfaEnrollmentRequiredResult, T> mfaEnrollmentMapper,
                          final Function<FailureResult, T> failureMapper) {
            return mfaEnrollmentMapper.apply(this);
        }

        static final class MapperMfaEnrollmentRequired<T> implements Mapper<T> {
            MapperMfaEnrollmentRequired(final MfaEnrollmentRequiredResult result) {
                Objects.requireNonNull(result, "MfaEnrollmentRequiredResult is required");
            }

            @Override
            public T orElse(final Function<FailureResult, T> failureMapper) {
                return failureMapper.apply(new FailureResult("MFA_ENROLLMENT_REQUIRED",
                    "MFA enrollment is required. Use the enrollment token to call SetupTotp."));
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
        public <T> Mapper<T> mapTo(final Function<SuccessResult, T> successMapper) {
            return new MapperFailure<>(this);
        }

        @Override
        public <T> T fold(final Function<SuccessResult, T> successMapper,
                          final Function<Required2FAResult, T> required2FAMapper,
                          final Function<PasswordResetRequiredResult, T> passwordResetMapper,
                          final Function<MfaEnrollmentRequiredResult, T> mfaEnrollmentMapper,
                          final Function<FailureResult, T> failureMapper) {
            return failureMapper.apply(this);
        }

        /**
         * Mapper returned when the result is a failure.
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
        /**
         * False positive: method IS tested (LoginResultTest) and used in production code;
         * tests intentionally ignore return value while validating behavior via side effects
         */
        @SuppressWarnings("UnusedReturnValue")
        T orElse(Function<FailureResult, T> failureMapper);
    }

}

