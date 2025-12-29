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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LoginCommandHandler using mocks.
 * Validates: command handler flow, error handling, rate limiting integration.
 * Full integration tests are in LoginIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class LoginCommandHandlerTest {

    @Mock
    private Ports.UserCredentialReader credentialReader;

    @Mock
    private Ports.UserRepository userRepository;

    @Mock
    private Ports.PasswordVerifier passwordHasher;

    @Mock
    private Ports.Clock clock;

    @Mock
    private Ports.TokenSigner tokenSigner;

    @Mock
    private Ports.RateLimiter rateLimiter;

    private LoginCommandHandler loginHandler;
    private UserCredential testCredential;
    private User testUser;

    @BeforeEach
    void setUp() {
        AuthenticationService authService = new AuthenticationService(passwordHasher);
        TokenService tokenService = new TokenService(clock, tokenSigner);
        loginHandler = new LoginCommandHandler(authService, tokenService, credentialReader, userRepository, rateLimiter);

        // Setup test data
        UserId userId = UserId.generate();
        PasswordHash passwordHash = new PasswordHash("$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
        testCredential = new UserCredential(userId, passwordHash);
        testUser = new User(userId, new Username("john_doe"), Set.of(Permission.of("read")));
    }

    private void mockSuccessfulFlow() {
        when(tokenSigner.sign(any(), any()))
            .thenAnswer(invocation -> Optional.of("signed.token"));
        when(rateLimiter.checkLimit(anyString()))
            .thenReturn(Ports.RateLimitResult.allowed());
        when(clock.now()).thenReturn(Instant.now());
    }

    @Test
    void testSuccessfulLogin() {
        mockSuccessfulFlow();
        when(credentialReader.findCredentialsByUsername(any())).thenReturn(Optional.of(testCredential));
        when(passwordHasher.verify(any())).thenReturn(Optional.of(testCredential.userId()));
        when(userRepository.findById(testCredential.userId())).thenReturn(Optional.of(testUser));

        LoginCommand cmd = new LoginCommand(Username.of("john_doe"), new Password("password123".toCharArray()), IpAddress.of("192.168.1.1"));
        LoginResult result = loginHandler.handle(cmd);

        result.mapTo(success -> {
            assertNotNull(success.tokenPair());
            assertEquals(testCredential.userId(), success.userId());
            assertNotNull(success.permissions());
            assertTrue(success.permissions().size() > 0);  // User should have some permissions
            return null;
        }).orElse(failure -> {
            fail("Login should succeed");
            return null;
        });
    }

    @Test
    void testLoginInvalidCredentials() {
        when(rateLimiter.checkLimit(anyString()))
            .thenReturn(Ports.RateLimitResult.allowed());
        when(credentialReader.findCredentialsByUsername(any())).thenReturn(Optional.of(testCredential));
        when(passwordHasher.verify(any())).thenReturn(Optional.empty());

        LoginCommand cmd = new LoginCommand(Username.of("john_doe"), new Password("wrongpass".toCharArray()), IpAddress.of("192.168.1.1"));
        LoginResult result = loginHandler.handle(cmd);

        result.mapTo(success -> {
            fail("Login should fail");
            return null;
        }).orElse(failure -> {
            assertEquals("INVALID_CREDENTIALS", failure.errorCode());
            return null;
        });
    }

    @Test
    void testLoginUnknownUser() {
        when(rateLimiter.checkLimit(anyString()))
            .thenReturn(Ports.RateLimitResult.allowed());
        when(credentialReader.findCredentialsByUsername(any())).thenReturn(Optional.empty());

        LoginCommand cmd = new LoginCommand(Username.of("unknown"), new Password("password".toCharArray()), IpAddress.of("192.168.1.1"));
        LoginResult result = loginHandler.handle(cmd);

        result.mapTo(success -> {
            fail("Login should fail");
            return null;
        }).orElse(failure -> {
            assertEquals("INVALID_CREDENTIALS", failure.errorCode());
            return null;
        });
    }

    @Test
    void testLoginRateLimited() {
        when(rateLimiter.checkLimit(anyString()))
            .thenReturn(Ports.RateLimitResult.blocked("Too many attempts"));

        LoginCommand cmd = new LoginCommand(Username.of("john_doe"), new Password("password".toCharArray()), IpAddress.of("192.168.1.1"));
        LoginResult result = loginHandler.handle(cmd);

        result.mapTo(success -> {
            fail("Should be rate limited");
            return null;
        }).orElse(failure -> {
            assertEquals("RATE_LIMITED", failure.errorCode());
            return null;
        });
    }

    @Test
    void testLoginNullCommand() {
        LoginResult result = loginHandler.handle(null);

        result.mapTo(success -> {
            fail("Should fail");
            return null;
        }).orElse(failure -> {
            assertEquals("INVALID_REQUEST", failure.errorCode());
            return null;
        });
    }

    @Test
    void testLoginRuntimeExceptionHandled() {
        when(rateLimiter.checkLimit(anyString()))
            .thenReturn(Ports.RateLimitResult.allowed());
        when(credentialReader.findCredentialsByUsername(any()))
            .thenThrow(new RuntimeException("Database error"));

        LoginCommand cmd = new LoginCommand(Username.of("john_doe"), new Password("password".toCharArray()), IpAddress.of("192.168.1.1"));
        LoginResult result = loginHandler.handle(cmd);

        result.mapTo(success -> {
            fail("Should fail");
            return null;
        }).orElse(failure -> {
            assertEquals("INTERNAL_ERROR", failure.errorCode());
            return null;
        });
    }
}
