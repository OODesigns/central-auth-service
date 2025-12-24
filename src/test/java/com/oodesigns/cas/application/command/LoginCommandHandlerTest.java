package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.*;
import com.oodesigns.cas.domain.value.IpAddress;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.Username;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration unit tests for LoginCommandHandler.
 * Validates: complete login flow, error handling, rate limiting, user authentication.
 */
@ExtendWith(MockitoExtension.class)
class LoginCommandHandlerTest {

    @Mock
    private Ports.UserRepositoryReader userRepository;

    @Mock
    private Ports.PasswordHasher passwordHasher;

    @Mock
    private Ports.Clock clock;

    @Mock
    private Ports.TokenSigner tokenSigner;

    @Mock
    private Ports.RateLimiter rateLimiter;

    private LoginCommandHandler loginHandler;
    private User testUser;
    private LoginCommand validCommand;

    @BeforeEach
    void setUp() {
        AuthenticationService authService = new AuthenticationService(passwordHasher, clock, tokenSigner);
        loginHandler = new LoginCommandHandler(authService, userRepository, rateLimiter);

        // Create test user
        UserId userId = UserId.generate();
        Username username = new Username("john_doe");
        PasswordHash passwordHash = new PasswordHash("$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
        testUser = User.create(userId, username, passwordHash);

        // Create valid command
        validCommand = new LoginCommand(Username.of("john_doe"), new Password("password123".toCharArray()), IpAddress.of("192.168.1.1"));
    }

    private void setupTokenSignerMock() {
        // Mock token signer to return simple signed tokens for testing
        when(tokenSigner.sign(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Instant.class)))
            .thenAnswer(invocation -> "signed." + invocation.getArgument(0));
    }

    @Test
    void testSuccessfulLogin() {
        setupTokenSignerMock();
        when(rateLimiter.checkLimit("login:192.168.1.1")).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify(org.mockito.ArgumentMatchers.any(char[].class), org.mockito.ArgumentMatchers.any(PasswordHash.class))).thenReturn(true);
        when(clock.now()).thenReturn(Instant.now());

        LoginResult result = loginHandler.handle(validCommand);

        result.mapTo(success -> {
                assertNotNull(success.tokenPair());
                assertNotNull(success.tokenPair().accessToken());
                assertNotNull(success.tokenPair().refreshToken());
                return null;
            })
            .orElse(failure -> {
                fail("Login should have succeeded");
                return null;
            });
        
        verify(rateLimiter).checkLimit("login:192.168.1.1");
        verify(userRepository).findByUsername(any(Username.class));
        verify(passwordHasher).verify(any(char[].class), any(PasswordHash.class));
    }

    @Test
    void testLoginWithInvalidCredentials() {
        when(rateLimiter.checkLimit("login:192.168.1.1")).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify(org.mockito.ArgumentMatchers.any(char[].class), org.mockito.ArgumentMatchers.any(PasswordHash.class))).thenReturn(false);

        LoginResult result = loginHandler.handle(validCommand);

        result.mapTo(success -> {
                fail("Login should have failed");
                return null;
            })
            .orElse(failure -> {
                assertEquals("INVALID_CREDENTIALS", failure.errorCode());
                return null;
            });
    }

    @Test
    void testLoginWithUnknownUser() {
        when(rateLimiter.checkLimit("login:192.168.1.1")).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.empty());

        LoginResult result = loginHandler.handle(validCommand);

        result.mapTo(success -> {
                fail("Login should have failed");
                return null;
            })
            .orElse(failure -> {
                assertEquals("INVALID_CREDENTIALS", failure.errorCode());
                return null;
            });
    }

    @Test
    void testLoginRateLimited() {
        when(rateLimiter.checkLimit("login:192.168.1.1")).thenReturn(createDeniedResult("Too many attempts from this IP"));

        LoginResult result = loginHandler.handle(validCommand);

        result.mapTo(success -> {
                fail("Login should have been rate limited");
                return null;
            })
            .orElse(failure -> {
                assertEquals("RATE_LIMITED", failure.errorCode());
                return null;
            });
    }

    @Test
    void testLoginWithInvalidUsername() {
        // LoginCommand validates at construction time - empty username rejected
        assertThrows(IllegalArgumentException.class, 
            () -> Username.of(""));
    }

    @Test
    void testLoginWithNullCommand() {
        LoginResult result = loginHandler.handle(null);

        result.mapTo(success -> {
                fail("Expected failure for null command");
                return null;
            })
            .orElse(failure -> {
                assertEquals("INVALID_REQUEST", failure.errorCode());
                assertEquals("LoginCommand cannot be null", failure.errorMessage());
                return null;
            });
    }

    @Test
    void testDifferentIPAddressesTrackSeparately() {
        setupTokenSignerMock();
        LoginCommand cmd2 = new LoginCommand(Username.of("john_doe"), new Password("password123".toCharArray()), IpAddress.of("192.168.1.2"));

        when(rateLimiter.checkLimit(org.mockito.ArgumentMatchers.anyString())).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify(org.mockito.ArgumentMatchers.any(char[].class), org.mockito.ArgumentMatchers.any(PasswordHash.class))).thenReturn(true);
        when(clock.now()).thenReturn(Instant.now());

        LoginResult result1 = loginHandler.handle(validCommand);
        LoginResult result2 = loginHandler.handle(cmd2);

        result1.mapTo(success -> { assertNotNull(success); return null; })
            .orElse(failure -> { fail("First result should succeed"); return null; });
        result2.mapTo(success -> { assertNotNull(success); return null; })
            .orElse(failure -> { fail("Second result should succeed"); return null; });
        
        verify(rateLimiter).checkLimit("login:192.168.1.1");
        verify(rateLimiter).checkLimit("login:192.168.1.2");
    }

    @Test
    void testRepositoryExceptionHandled() {
        when(rateLimiter.checkLimit("login:192.168.1.1")).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class)))
            .thenThrow(new RuntimeException("Database connection failed"));

        LoginResult result = loginHandler.handle(validCommand);

        result.mapTo(success -> {
                fail("Login should have failed");
                return null;
            })
            .orElse(failure -> {
                assertEquals("INTERNAL_ERROR", failure.errorCode());
                return null;
            });
    }

    @Test
    void testPasswordHasherExceptionHandled() {
        when(rateLimiter.checkLimit("login:192.168.1.1")).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify(org.mockito.ArgumentMatchers.any(char[].class), org.mockito.ArgumentMatchers.any(PasswordHash.class)))
            .thenThrow(new RuntimeException("Bcrypt error"));

        LoginResult result = loginHandler.handle(validCommand);

        result.mapTo(success -> {
                fail("Login should have failed");
                return null;
            })
            .orElse(failure -> {
                assertEquals("INTERNAL_ERROR", failure.errorCode());
                return null;
            });
    }

    @Test
    void testCompleteFlowWithAdminUser() {
        setupTokenSignerMock();
        User adminUser = testUser.grantPermission(Permission.of("manage_users"));

        when(rateLimiter.checkLimit("login:192.168.1.1")).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(adminUser));
        when(passwordHasher.verify(org.mockito.ArgumentMatchers.any(char[].class), org.mockito.ArgumentMatchers.any(PasswordHash.class))).thenReturn(true);
        when(clock.now()).thenReturn(Instant.now());

        LoginResult result = loginHandler.handle(validCommand);

        result.mapTo(success -> {
                assertNotNull(success.tokenPair());
                assertNotNull(success.tokenPair().accessToken());
                assertNotNull(success.tokenPair().refreshToken());
                return null;
            })
            .orElse(failure -> {
                fail("Login should have succeeded");
                return null;
            });
    }

    @Test
    void testLoginCommandViaHandler() {
        setupTokenSignerMock();
        char[] password = "mypass".toCharArray();
        LoginCommand cmd = new LoginCommand(Username.of("test_user"), new Password(password), IpAddress.of("10.0.0.1"));

        when(rateLimiter.checkLimit("login:10.0.0.1")).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify(org.mockito.ArgumentMatchers.any(char[].class), org.mockito.ArgumentMatchers.any(PasswordHash.class))).thenReturn(true);
        when(clock.now()).thenReturn(Instant.now());

        LoginResult result = loginHandler.handle(cmd);

        result.mapTo(success -> {
                assertNotNull(success.tokenPair());
                return null;
            })
            .orElse(failure -> {
                fail("Login should have succeeded");
                return null;
            });
    }

    @Test
    void testMultipleLoginAttemptsWithRateLimiting() {
        setupTokenSignerMock();
        when(rateLimiter.checkLimit("login:192.168.1.1"))
            .thenReturn(createAllowedResult())
            .thenReturn(createAllowedResult())
            .thenReturn(createDeniedResult("Rate limited"));

        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify(org.mockito.ArgumentMatchers.any(char[].class), org.mockito.ArgumentMatchers.any(PasswordHash.class))).thenReturn(true);
        when(clock.now()).thenReturn(Instant.now());

        LoginResult result1 = loginHandler.handle(validCommand);
        result1.mapTo(success -> { assertNotNull(success); return null; })
            .orElse(failure -> { fail("Should have succeeded"); return null; });

        LoginResult result2 = loginHandler.handle(validCommand);
        result2.mapTo(success -> { assertNotNull(success); return null; })
            .orElse(failure -> { fail("Should have succeeded"); return null; });

        LoginResult result3 = loginHandler.handle(validCommand);
        result3.mapTo(success -> { fail("Should have been rate limited"); return null; })
            .orElse(failure -> {
                assertEquals("RATE_LIMITED", failure.errorCode());
                return null;
            });
    }

    @Test
    void testErrorMessagesAreSafe() {
        when(rateLimiter.checkLimit("login:192.168.1.1")).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(testUser));

        LoginResult result = loginHandler.handle(validCommand);

        result.mapTo(success -> { fail("Login should have failed"); return null; })
            .orElse(failure -> {
                String errorMsg = failure.errorMessage();
                assertFalse(errorMsg.contains("Optional.empty"));
                assertFalse(errorMsg.contains("null"));
                assertTrue(errorMsg.contains("Invalid username or password"));
                return null;
            });
    }

    /**
     * Helper to create a RateLimitResult for testing.
     */
    private Ports.RateLimitResult createAllowedResult() {
        return Ports.RateLimitResult.allowed();
    }

    /**
     * Helper to create a denied RateLimitResult for testing.
     */
    private Ports.RateLimitResult createDeniedResult(String message) {
        return Ports.RateLimitResult.blocked(message);
    }
}
