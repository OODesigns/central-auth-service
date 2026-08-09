package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LoginCommandHandler using mocks.
 * Validates: command handler flow, error handling, rate limiting integration.
 * Full integration tests are in LoginIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class LoginCommandHandlerTest {

    private static final String VALID_PASSWORD = "ValidPassword1234";  // 16 chars

    @Mock
    private Ports.UserCredentialRetriever credentialReader;

    @Mock
    private Ports.UserRetriever userRepository;

    @Mock
    private Ports.PasswordVerifier passwordHasher;

    @Mock
    private Ports.Clock clock;

    @Mock
    private Ports.TokenSigner tokenSigner;

    @Mock
    private Ports.RateLimiter rateLimiter;

    @Mock
    private Ports.TotpStatusReader totpStatusReader;

    private LoginCommandHandler loginHandler;
    private UserCredential testCredential;
    private User testUser;

    @BeforeEach
    void setUp() {
        final AuthenticationService authService = new AuthenticationService(passwordHasher);
        final TokenService tokenService = new TokenService(clock, tokenSigner);
        loginHandler = new LoginCommandHandler(authService, tokenService, credentialReader, userRepository, totpStatusReader, rateLimiter);

        // Setup test data
        final UserId userId = UserId.of(UUID.randomUUID());
        final PasswordHash passwordHash = PasswordHash.of("$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
        testCredential = UserCredential.of(userId, passwordHash);
        // Create test user with no password reset required and no 2FA required
        testUser = new User(userId, Username.of("john_doe"), Set.of(Permission.of("read")), null, null);
    }

    private void mockSuccessfulFlow() {
        when(tokenSigner.sign(any(), any()))
            .thenAnswer(ignored -> Optional.of("signed.token"));
        when(rateLimiter.checkLimit(any(LoginCommand.class)))
            .thenReturn(Ports.RateLimitResult.allowed());
        when(clock.now()).thenReturn(Instant.now());
        // Mock: 2FA is disabled by default
        when(totpStatusReader.check2FAStatus(any())).thenReturn(Optional.empty());
    }

    @Test
    void testSuccessfulLogin() {
        mockSuccessfulFlow();
        when(credentialReader.findCredentialsByUsername(any())).thenReturn(Optional.of(testCredential));
        when(passwordHasher.verify(any())).thenReturn(Optional.of(testCredential.userId()));
        when(userRepository.findById(testCredential.userId())).thenReturn(Optional.of(testUser));

        final LoginCommand cmd = new LoginCommand(Username.of("john_doe"), Password.of(VALID_PASSWORD.toCharArray()), IpAddress.of("192.168.1.1"));
        final LoginResult result = loginHandler.handle(cmd);

        result.mapTo(success -> {
            assertNotNull(success.tokenPair());
            assertEquals(testCredential.userId(), success.userId());
            assertNotNull(success.permissions());
            assertFalse(success.permissions().isEmpty());  // User should have some permissions
            return null;
        }).orElse(ignored -> {
            fail("Login should succeed");
            return null;
        });
    }

    @Test
    void testLoginInvalidCredentials() {
        when(rateLimiter.checkLimit(any(LoginCommand.class)))
            .thenReturn(Ports.RateLimitResult.allowed());
        when(credentialReader.findCredentialsByUsername(any())).thenReturn(Optional.of(testCredential));
        when(passwordHasher.verify(any())).thenReturn(Optional.empty());

        final LoginCommand cmd = new LoginCommand(Username.of("john_doe"), Password.of("WrongPassword123".toCharArray()), IpAddress.of("192.168.1.1"));  // 15 chars
        final LoginResult result = loginHandler.handle(cmd);

        result.mapTo(ignored -> {
            fail("Login should fail");
            return null;
        }).orElse(failure -> {
            assertEquals("INVALID_CREDENTIALS", failure.errorCode());
            return null;
        });
    }

    @Test
    void testLoginUnknownUser() {
        when(rateLimiter.checkLimit(any(LoginCommand.class)))
            .thenReturn(Ports.RateLimitResult.allowed());
        when(credentialReader.findCredentialsByUsername(any())).thenReturn(Optional.empty());

        final LoginCommand cmd = new LoginCommand(Username.of("unknown"), Password.of(VALID_PASSWORD.toCharArray()), IpAddress.of("192.168.1.1"));
        final LoginResult result = loginHandler.handle(cmd);

        result.mapTo(ignored -> {
            fail("Login should fail");
            return null;
        }).orElse(failure -> {
            assertEquals("INVALID_CREDENTIALS", failure.errorCode());
            return null;
        });
    }

    @Test
    void testLoginRateLimited() {
        when(rateLimiter.checkLimit(any(LoginCommand.class)))
            .thenReturn(Ports.RateLimitResult.blocked("Too many attempts"));

        final LoginCommand cmd = new LoginCommand(Username.of("john_doe"), Password.of(VALID_PASSWORD.toCharArray()), IpAddress.of("192.168.1.1"));
        final LoginResult result = loginHandler.handle(cmd);

        result.mapTo(ignored -> {
            fail("Should be rate limited");
            return null;
        }).orElse(failure -> {
            assertEquals("RATE_LIMITED", failure.errorCode());
            return null;
        });
    }

    @Test
    void testLoginNullCommand() {
        final LoginResult result = loginHandler.handle(null);

        result.mapTo(ignored -> {
            fail("Should fail");
            return null;
        }).orElse(failure -> {
            assertEquals("INVALID_REQUEST", failure.errorCode());
            return null;
        });
    }

    @Test
    void testLoginRuntimeExceptionHandled() {
        // Suppress logger output for this test since we're intentionally testing exception handling
        final Logger logger = Logger.getLogger(LoginCommandHandler.class.getName());
        final Level originalLevel = logger.getLevel();

        try (var _ = new AutoCloseable() {
            { logger.setLevel(Level.OFF); }

            @Override
            public void close() {
                logger.setLevel(originalLevel);
            }
        }) {
            when(rateLimiter.checkLimit(any(LoginCommand.class)))
                .thenReturn(Ports.RateLimitResult.allowed());
            when(credentialReader.findCredentialsByUsername(any()))
                .thenThrow(new RuntimeException("Database error"));

            final LoginCommand cmd = new LoginCommand(Username.of("john_doe"), Password.of(VALID_PASSWORD.toCharArray()), IpAddress.of("192.168.1.1"));
            final LoginResult result = loginHandler.handle(cmd);

            result.mapTo(_ -> {
                fail("Should fail");
                return null;
            }).orElse(failure -> {
                assertEquals("INTERNAL_ERROR", failure.errorCode());
                return null;
            });
        } catch (final Exception e) {
            // Should not happen with this AutoCloseable implementation
            throw new AssertionError(e);
        }
    }

    @Test
    void testLoginRequires2FAWhenEnabled() {
        // Set up only the stubs we need for this test to avoid unnecessary stubbing
        when(tokenSigner.sign(any(), any())).thenReturn(Optional.of("signed.token"));
        when(rateLimiter.checkLimit(any(LoginCommand.class))).thenReturn(Ports.RateLimitResult.allowed());
        when(clock.now()).thenReturn(java.time.Instant.now());
        when(credentialReader.findCredentialsByUsername(any())).thenReturn(Optional.of(testCredential));
        when(passwordHasher.verify(any())).thenReturn(Optional.of(testCredential.userId()));
        // Simulate 2FA enabled for the user
        when(totpStatusReader.check2FAStatus(testCredential.userId())).thenReturn(Optional.of(testCredential.userId()));

        final LoginCommand cmd = new LoginCommand(Username.of("john_doe"), Password.of(VALID_PASSWORD.toCharArray()), IpAddress.of("192.168.1.1"));
        final LoginResult result = loginHandler.handle(cmd);

        result.mapTo(success -> {
            fail("Expected 2FA required result");
            return null;
        }).orElse(failure -> {
            // Required2FAResult maps to MFA_SETUP_REQUIRED in orElse path per LoginResult implementation
            assertEquals("MFA_SETUP_REQUIRED", failure.errorCode());
            return null;
        });
    }
}
