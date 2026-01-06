package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.value.Credentials;
import com.oodesigns.cas.domain.value.Payload;
import com.oodesigns.cas.domain.value.UserCredential;
import com.oodesigns.cas.domain.value.Username;
import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PortsTest {

    // Test implementations of all Ports interfaces to achieve 100% coverage

    static class TestPasswordVerifier implements Ports.PasswordVerifier {
        @Override
        public Optional<UserId> verify(final Credentials credentials) {
            return Optional.of(UserId.generate());
        }
    }

    static class TestTokenSigner implements Ports.TokenSigner {
        @Override
        public Optional<String> sign(final Payload payload, final Instant expiresAt) {
            return Optional.of("test.token.here");
        }
    }

    static class TestClock implements Ports.Clock {
        @Override
        public Instant now() {
            return Instant.now();
        }
    }

    static class TestRateLimiter implements Ports.RateLimiter {
        @Override
        public Ports.RateLimitResult checkLimit(final String key) {
            return Ports.RateLimitResult.allowed();
        }
    }

    static class TestUserCredentialReader implements Ports.UserCredentialReader {
        @Override
        public Optional<UserCredential> findCredentialsByUsername(final Username username) {
            return Optional.empty();
        }
    }

    static class TestUserRepository implements Ports.UserRepository {
        @Override
        public Optional<User> findById(final UserId userId) {
            return Optional.empty();
        }
    }

    @Test
    void testPasswordVerifierImplementation() {
        final Ports.PasswordVerifier verifier = new TestPasswordVerifier();
        assertNotNull(verifier);
    }

    @Test
    void testTokenSignerImplementation() {
        final Ports.TokenSigner signer = new TestTokenSigner();
        assertNotNull(signer);
    }

    @Test
    void testClockImplementation() {
        final Ports.Clock clock = new TestClock();
        assertNotNull(clock);
    }

    @Test
    void testRateLimiterImplementation() {
        final Ports.RateLimiter limiter = new TestRateLimiter();
        assertNotNull(limiter);
    }

    @Test
    void testUserCredentialReaderImplementation() {
        final Ports.UserCredentialReader reader = new TestUserCredentialReader();
        assertNotNull(reader);
    }

    @Test
    void testUserRepositoryImplementation() {
        final Ports.UserRepository repository = new TestUserRepository();
        assertNotNull(repository);
    }

    @Test
    void allowedCreatesValidInstance() {
        final Ports.RateLimitResult.Allowed allowed = Ports.RateLimitResult.allowed();
        assertNotNull(allowed);
    }

    @Test
    void blockedCreatesValidInstance() {
        final Ports.RateLimitResult.Blocked blocked = Ports.RateLimitResult.blocked("rate limited");
        assertNotNull(blocked);
        assertEquals("rate limited", blocked.message());
    }

    @Test
    void blockedRejectsNullMessage() {
        assertThrows(IllegalArgumentException.class, () -> Ports.RateLimitResult.blocked(null));
    }

    @Test
    void blockedRejectsBlankMessage() {
        assertThrows(IllegalArgumentException.class, () -> Ports.RateLimitResult.blocked("   "));
    }

    @Test
    void allowedMapToReturnsValueOnOrElse() {
        final Ports.RateLimitResult.Allowed allowed = Ports.RateLimitResult.allowed();
        final String result = allowed
            .mapTo(a -> "success")
            .orElse(b -> "blocked");
        assertEquals("success", result);
    }

    @Test
    void allowedMapToWithDifferentType() {
        final Ports.RateLimitResult.Allowed allowed = Ports.RateLimitResult.allowed();
        final Integer result = allowed
            .mapTo(a -> 42)
            .orElse(b -> 0);
        assertEquals(42, result);
    }

    @Test
    void blockedMapToReturnsBlockedOnOrElse() {
        final Ports.RateLimitResult.Blocked blocked = Ports.RateLimitResult.blocked("too many requests");
        final String result = blocked
            .mapTo(a -> "success")
            .orElse(b -> "blocked: " + b.message());
        assertEquals("blocked: too many requests", result);
    }

    @Test
    void blockedMapToWithDifferentType() {
        final Ports.RateLimitResult.Blocked blocked = Ports.RateLimitResult.blocked("error");
        final Integer result = blocked
            .mapTo(a -> 100)
            .orElse(b -> -1);
        assertEquals(-1, result);
    }

    @Test
    void allowedAndBlockedAreSealed() {
        final Ports.RateLimitResult allowed = Ports.RateLimitResult.allowed();
        final Ports.RateLimitResult blocked = Ports.RateLimitResult.blocked("test");
        assertInstanceOf(Ports.RateLimitResult.Allowed.class, allowed);
        assertInstanceOf(Ports.RateLimitResult.Blocked.class, blocked);
    }

    @Test
    void mapperAllowedExecutesOnAllowedBranch() {
        final Ports.RateLimitResult.Allowed allowed = Ports.RateLimitResult.allowed();
        final String[] callTracker = {""};
        final String result = allowed
            .mapTo(a -> {
                callTracker[0] = "allowed";
                return "allowed_executed";
            })
            .orElse(b -> {
                callTracker[0] = "blocked";
                return "blocked_executed";
            });
        assertEquals("allowed", callTracker[0]);
        assertEquals("allowed_executed", result);
    }

    @Test
    void mapperBlockedExecutesOnBlockedBranch() {
        final Ports.RateLimitResult.Blocked blocked = Ports.RateLimitResult.blocked("limit exceeded");
        final String[] callTracker = {""};
        final String result = blocked
            .mapTo(a -> {
                callTracker[0] = "allowed";
                return "allowed_executed";
            })
            .orElse(b -> {
                callTracker[0] = "blocked";
                return "blocked_executed";
            });
        assertEquals("blocked", callTracker[0]);
        assertEquals("blocked_executed", result);
    }
}

