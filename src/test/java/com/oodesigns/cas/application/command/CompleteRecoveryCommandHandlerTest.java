package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.RecoveryToken;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompleteRecoveryCommandHandlerTest {
    private static final RecoveryToken TOKEN = RecoveryToken.of("header.payload.signature");
    private static final com.oodesigns.cas.domain.value.UserId USER_ID =
        com.oodesigns.cas.domain.value.UserId.of(UUID.randomUUID());
    private static final com.oodesigns.cas.domain.value.PasswordHash HASH =
        com.oodesigns.cas.domain.value.PasswordHash.of("$2a$10$12345678901234567890123456789012345678901234567890123");
    @Test
    void rejectsInvalidTokenBeforeHashingOrWritingPassword() {
        final RejectingVerifier tokenVerifier = new RejectingVerifier();
        final ThrowingHasher passwordHasher = new ThrowingHasher();
        final ThrowingStore recoveryTokenStore = new ThrowingStore();
        final CompleteRecoveryCommandHandler handler =
                new CompleteRecoveryCommandHandler(tokenVerifier, passwordHasher, recoveryTokenStore);
        final char[] passwordChars = "securepassword123".toCharArray();

        final CompleteRecoveryResult result = handler.handle(new CompleteRecoveryCommand(TOKEN, Password.of(passwordChars)));

        assertEquals("INVALID_RECOVERY_TOKEN", result.fold(success -> null, failure -> failure.errorCode()));
    }

        @Test
        void rejectsMissingCommand() {
        final CompleteRecoveryCommandHandler handler = new CompleteRecoveryCommandHandler(
            new RejectingVerifier(), new ThrowingHasher(), new ThrowingStore());

        assertEquals("INVALID_REQUEST", handler.handle(null).fold(success -> null, failure -> failure.errorCode()));
        }

        @Test
        void completesRecoveryWhenTokenAndStoreAreValid() {
        final CompleteRecoveryCommandHandler handler = new CompleteRecoveryCommandHandler(
            new AcceptingVerifier(), password -> HASH, new CompletingStore());

        final CompleteRecoveryResult result = handler.handle(new CompleteRecoveryCommand(
            TOKEN, Password.of("securepassword123".toCharArray())));

        assertEquals("completed", result.fold(success -> "completed", failure -> failure.errorCode()));
        }

        @Test
        void returnsInvalidFailureWhenStoreRejectsToken() {
        final CompleteRecoveryCommandHandler handler = new CompleteRecoveryCommandHandler(
            new AcceptingVerifier(), password -> HASH, new RejectingStore());

        assertEquals("INVALID_RECOVERY_TOKEN", handler.handle(new CompleteRecoveryCommand(
            TOKEN, Password.of("securepassword123".toCharArray())))
            .fold(success -> null, failure -> failure.errorCode()));
        }

        @Test
        void returnsInternalFailureWhenHashingFails() {
        final CompleteRecoveryCommandHandler handler = new CompleteRecoveryCommandHandler(
            new AcceptingVerifier(), password -> { throw new IllegalStateException("hash failure"); }, new CompletingStore());

        assertEquals("INTERNAL_ERROR", handler.handle(new CompleteRecoveryCommand(
            TOKEN, Password.of("securepassword123".toCharArray())))
            .fold(success -> null, failure -> failure.errorCode()));
        }

        @Test
        void validatesResultVariantsAndDependencies() {
        assertThrows(NullPointerException.class, () -> new CompleteRecoveryCommandHandler(null, password -> HASH, new CompletingStore()));
        assertThrows(NullPointerException.class, () -> new CompleteRecoveryCommandHandler(new AcceptingVerifier(), null, new CompletingStore()));
        assertThrows(NullPointerException.class, () -> new CompleteRecoveryCommandHandler(new AcceptingVerifier(), password -> HASH, null));
        assertEquals("completed", CompleteRecoveryResult.success().fold(success -> "completed", failure -> null));
        assertEquals("ERROR", CompleteRecoveryResult.failure("ERROR", "message")
            .fold(success -> null, failure -> failure.errorCode()));
        }

    private static class RejectingVerifier implements Ports.TokenVerifier {
        @Override public Optional<Ports.AccessTokenClaims> verifyAccessToken(final com.oodesigns.cas.domain.value.AccessToken token) { return Optional.empty(); }
        @Override public Optional<com.oodesigns.cas.domain.value.UserId> verify2FAVerificationToken(final com.oodesigns.cas.domain.value.TwoFactorVerificationToken token) { return Optional.empty(); }
        @Override public Optional<com.oodesigns.cas.domain.value.UserId> verifyMfaEnrollmentToken(final com.oodesigns.cas.domain.value.MfaEnrollmentToken token) { return Optional.empty(); }
        @Override public Optional<com.oodesigns.cas.domain.value.UserId> verifyRefreshToken(final com.oodesigns.cas.domain.value.RefreshToken token) { return Optional.empty(); }
        @Override public Optional<com.oodesigns.cas.domain.value.UserId> verifyRecoveryToken(final RecoveryToken token) { return Optional.empty(); }
    }

    private static final class ThrowingHasher implements Ports.PasswordHasher {
        @Override public com.oodesigns.cas.domain.value.PasswordHash hash(final Password password) { throw new AssertionError("Must not hash invalid recovery request"); }
    }

    private static class ThrowingStore implements Ports.RecoveryTokenStore {
        @Override public void issue(final com.oodesigns.cas.domain.value.UserId administrator, final com.oodesigns.cas.domain.value.UserId target, final RecoveryToken token) { throw new AssertionError("Must not issue"); }
        @Override public RecoveryCompletion consumeAndReset(final com.oodesigns.cas.domain.value.UserId target, final RecoveryToken token,
                                                              final com.oodesigns.cas.domain.value.PasswordHash passwordHash) { throw new AssertionError("Must not persist invalid recovery request"); }
    }

    private static final class AcceptingVerifier extends RejectingVerifier {
        @Override public Optional<com.oodesigns.cas.domain.value.UserId> verifyRecoveryToken(final RecoveryToken token) {
            return Optional.of(USER_ID);
        }
    }

    private static class CompletingStore extends ThrowingStore {
        @Override public RecoveryCompletion consumeAndReset(final com.oodesigns.cas.domain.value.UserId target,
                                                              final RecoveryToken token,
                                                              final com.oodesigns.cas.domain.value.PasswordHash passwordHash) {
            return RecoveryCompletion.COMPLETED;
        }
    }

    private static final class RejectingStore extends CompletingStore {
        @Override public RecoveryCompletion consumeAndReset(final com.oodesigns.cas.domain.value.UserId target,
                                                              final RecoveryToken token,
                                                              final com.oodesigns.cas.domain.value.PasswordHash passwordHash) {
            return RecoveryCompletion.INVALID_OR_CONSUMED;
        }
    }
}