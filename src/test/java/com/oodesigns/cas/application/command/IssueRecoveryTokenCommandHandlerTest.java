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

class IssueRecoveryTokenCommandHandlerTest {
    @Test
    void issuesSignedTokenAndPersistsOnlyThroughRecoveryStore() {
        final UserId administratorId = UserId.of(UUID.randomUUID());
        final UserId targetUserId = UserId.of(UUID.randomUUID());
        final RecordingStore recoveryTokenStore = new RecordingStore();
        final TokenService tokenService = new TokenService(() -> Instant.ofEpochSecond(1_700_000_000L),
                new RecoverySigner());
        final IssueRecoveryTokenCommandHandler handler =
                new IssueRecoveryTokenCommandHandler(tokenService, recoveryTokenStore);

        final IssueRecoveryTokenResult result = handler.handle(new IssueRecoveryTokenCommand(administratorId, targetUserId));

        assertEquals("header.payload.signature", result.fold(success -> success.token().value(), failure -> null));
        assertEquals(administratorId, recoveryTokenStore.administratorId);
        assertEquals(targetUserId, recoveryTokenStore.targetUserId);
        assertEquals("header.payload.signature", recoveryTokenStore.token.value());
    }

    private static final class RecoverySigner implements Ports.TokenSigner {
        @Override public java.util.Optional<com.oodesigns.cas.domain.value.AccessToken> signAccessToken(final Payload payload, final Instant expiry) { return java.util.Optional.empty(); }
        @Override public java.util.Optional<com.oodesigns.cas.domain.value.RefreshToken> signRefreshToken(final Payload payload, final Instant expiry) { return java.util.Optional.empty(); }
        @Override public java.util.Optional<com.oodesigns.cas.domain.value.TwoFactorVerificationToken> signTwoFactorVerificationToken(final Payload payload, final Instant expiry) { return java.util.Optional.empty(); }
        @Override public java.util.Optional<com.oodesigns.cas.domain.value.MfaEnrollmentToken> signMfaEnrollmentToken(final Payload payload, final Instant expiry) { return java.util.Optional.empty(); }
        @Override public java.util.Optional<RecoveryToken> signRecoveryToken(final Payload payload, final Instant expiry) { return java.util.Optional.of(RecoveryToken.of("header.payload.signature")); }
    }

    private static final class RecordingStore implements Ports.RecoveryTokenStore {
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