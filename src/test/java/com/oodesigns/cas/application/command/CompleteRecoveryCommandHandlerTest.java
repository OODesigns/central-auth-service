package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.RecoveryToken;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompleteRecoveryCommandHandlerTest {
    @Test
    void rejectsInvalidTokenBeforeHashingOrWritingPassword() {
        final RecoveryToken token = RecoveryToken.of("header.payload.signature");
    final RejectingVerifier tokenVerifier = new RejectingVerifier();
    final ThrowingHasher passwordHasher = new ThrowingHasher();
    final ThrowingStore recoveryTokenStore = new ThrowingStore();
        final CompleteRecoveryCommandHandler handler =
                new CompleteRecoveryCommandHandler(tokenVerifier, passwordHasher, recoveryTokenStore);
        final char[] passwordChars = "securepassword123".toCharArray();

        final CompleteRecoveryResult result = handler.handle(new CompleteRecoveryCommand(token, Password.of(passwordChars)));

        assertEquals("INVALID_RECOVERY_TOKEN", result.fold(success -> null, failure -> failure.errorCode()));
    }

    private static final class RejectingVerifier implements Ports.TokenVerifier {
        @Override public Optional<Ports.AccessTokenClaims> verifyAccessToken(final com.oodesigns.cas.domain.value.AccessToken token) { return Optional.empty(); }
        @Override public Optional<com.oodesigns.cas.domain.value.UserId> verify2FAVerificationToken(final com.oodesigns.cas.domain.value.TwoFactorVerificationToken token) { return Optional.empty(); }
        @Override public Optional<com.oodesigns.cas.domain.value.UserId> verifyMfaEnrollmentToken(final com.oodesigns.cas.domain.value.MfaEnrollmentToken token) { return Optional.empty(); }
        @Override public Optional<com.oodesigns.cas.domain.value.UserId> verifyRefreshToken(final com.oodesigns.cas.domain.value.RefreshToken token) { return Optional.empty(); }
        @Override public Optional<com.oodesigns.cas.domain.value.UserId> verifyRecoveryToken(final RecoveryToken token) { return Optional.empty(); }
    }

    private static final class ThrowingHasher implements Ports.PasswordHasher {
        @Override public com.oodesigns.cas.domain.value.PasswordHash hash(final Password password) { throw new AssertionError("Must not hash invalid recovery request"); }
    }

    private static final class ThrowingStore implements Ports.RecoveryTokenStore {
        @Override public void issue(final com.oodesigns.cas.domain.value.UserId administrator, final com.oodesigns.cas.domain.value.UserId target, final RecoveryToken token) { throw new AssertionError("Must not issue"); }
        @Override public RecoveryCompletion consumeAndReset(final com.oodesigns.cas.domain.value.UserId target, final RecoveryToken token,
                                                              final com.oodesigns.cas.domain.value.PasswordHash passwordHash) { throw new AssertionError("Must not persist invalid recovery request"); }
    }
}