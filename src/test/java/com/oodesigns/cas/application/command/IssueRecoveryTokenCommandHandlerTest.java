package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.value.Payload;
import com.oodesigns.cas.domain.value.RecoveryToken;
import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssueRecoveryTokenCommandHandlerTest {
    private static final UserId ADMINISTRATOR_ID = UserId.of(UUID.randomUUID());
    private static final UserId TARGET_USER_ID = UserId.of(UUID.randomUUID());

    @Test
    void issuesSignedTokenAndPersistsOnlyThroughRecoveryStore() {
        final RecordingStore recoveryTokenStore = new RecordingStore();
        final TokenService tokenService = new TokenService(() -> Instant.ofEpochSecond(1_700_000_000L),
                new RecoverySigner());
        final IssueRecoveryTokenCommandHandler handler =
                new IssueRecoveryTokenCommandHandler(tokenService, recoveryTokenStore);

        final IssueRecoveryTokenResult result = handler.handle(new IssueRecoveryTokenCommand(ADMINISTRATOR_ID, TARGET_USER_ID));

        assertEquals("header.payload.signature", result.fold(success -> success.token().value(), failure -> null));
        assertEquals(ADMINISTRATOR_ID, recoveryTokenStore.administratorId);
        assertEquals(TARGET_USER_ID, recoveryTokenStore.targetUserId);
        assertEquals("header.payload.signature", recoveryTokenStore.token.value());
    }

    @Test
    void rejectsMissingCommand() {
        final IssueRecoveryTokenCommandHandler handler = new IssueRecoveryTokenCommandHandler(
                new TokenService(() -> Instant.ofEpochSecond(1_700_000_000L), new RecoverySigner()),
                new RecordingStore());

        assertEquals("INVALID_REQUEST", handler.handle(null).fold(success -> null, failure -> failure.errorCode()));
    }

    @Test
    void returnsInternalFailureWhenTokenSigningFails() {
        final Ports.TokenSigner signer = new RecoverySigner() {
            @Override
            public java.util.Optional<RecoveryToken> signRecoveryToken(final Payload payload, final Instant expiry) {
                return java.util.Optional.empty();
            }
        };
        final IssueRecoveryTokenCommandHandler handler = new IssueRecoveryTokenCommandHandler(
                new TokenService(() -> Instant.ofEpochSecond(1_700_000_000L), signer), new RecordingStore());

        assertEquals("INTERNAL_ERROR", handler.handle(new IssueRecoveryTokenCommand(ADMINISTRATOR_ID, TARGET_USER_ID))
                .fold(success -> null, failure -> failure.errorCode()));
    }

    @Test
    void returnsInternalFailureWhenStoreFails() {
        final Ports.RecoveryTokenStore failingStore = new RecordingStore() {
            @Override
            public void issue(final UserId administrator, final UserId target, final RecoveryToken token) {
                throw new IllegalStateException("database unavailable");
            }
        };
        final IssueRecoveryTokenCommandHandler handler = new IssueRecoveryTokenCommandHandler(
                new TokenService(() -> Instant.ofEpochSecond(1_700_000_000L), new RecoverySigner()), failingStore);

        assertEquals("INTERNAL_ERROR", handler.handle(new IssueRecoveryTokenCommand(ADMINISTRATOR_ID, TARGET_USER_ID))
                .fold(success -> null, failure -> failure.errorCode()));
    }

    @Test
    void validatesResultVariantsAndDependencies() {
        assertThrows(NullPointerException.class, () -> new IssueRecoveryTokenCommandHandler(null, new RecordingStore()));
        assertThrows(NullPointerException.class, () -> new IssueRecoveryTokenCommandHandler(
                new TokenService(() -> Instant.now(), new RecoverySigner()), null));
        assertEquals("header.payload.signature", IssueRecoveryTokenResult.success(RecoveryToken.of("header.payload.signature"))
                .fold(success -> success.token().value(), failure -> null));
        assertEquals("ERROR", IssueRecoveryTokenResult.failure("ERROR", "message")
                .fold(success -> null, failure -> failure.errorCode()));
    }

    private static class RecoverySigner implements Ports.TokenSigner {
        @Override public java.util.Optional<com.oodesigns.cas.domain.value.AccessToken> signAccessToken(final Payload payload, final Instant expiry) { return java.util.Optional.empty(); }
        @Override public java.util.Optional<com.oodesigns.cas.domain.value.RefreshToken> signRefreshToken(final Payload payload, final Instant expiry) { return java.util.Optional.empty(); }
        @Override public java.util.Optional<com.oodesigns.cas.domain.value.TwoFactorVerificationToken> signTwoFactorVerificationToken(final Payload payload, final Instant expiry) { return java.util.Optional.empty(); }
        @Override public java.util.Optional<com.oodesigns.cas.domain.value.MfaEnrollmentToken> signMfaEnrollmentToken(final Payload payload, final Instant expiry) { return java.util.Optional.empty(); }
        @Override public java.util.Optional<RecoveryToken> signRecoveryToken(final Payload payload, final Instant expiry) { return java.util.Optional.of(RecoveryToken.of("header.payload.signature")); }
    }

    private static class RecordingStore implements Ports.RecoveryTokenStore {
        private UserId administratorId;
        private UserId targetUserId;
        private RecoveryToken token;
        @Override public void issue(final UserId administrator, final UserId target, final RecoveryToken recoveryToken) {
            administratorId = administrator; targetUserId = target; token = recoveryToken;
        }
        @Override public RecoveryCompletion consumeAndReset(final UserId target, final RecoveryToken recoveryToken,
                                                              final com.oodesigns.cas.domain.value.PasswordHash passwordHash) {
            return RecoveryCompletion.INVALID_OR_CONSUMED;
        }
    }
}