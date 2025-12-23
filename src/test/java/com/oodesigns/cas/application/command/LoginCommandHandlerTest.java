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
        // Mock token signer to return simple signed tokens for testing
        when(tokenSigner.sign(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Instant.class)))
            .thenAnswer(invocation -> "signed." + invocation.getArgument(0));
        
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

    @Test
    void testSuccessfulLogin() {
        when(rateLimiter.checkLimit("login:192.168.1.1")).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(PasswordHash.class))).thenReturn(true);
        when(clock.now()).thenReturn(Instant.now());

        LoginResult result = loginHandler.handle(validCommand);

        assertTrue(result.isSuccess());
        assertNotNull(result.getAccessToken());
        assertNotNull(result.getRefreshToken());
        
        verify(rateLimiter).checkLimit("login:192.168.1.1");
        verify(userRepository).findByUsername(any(Username.class));
        verify(passwordHasher).verify(anyString(), any(PasswordHash.class));
    }

    @Test
    void testLoginWithInvalidCredentials() {
        when(rateLimiter.checkLimit("login:192.168.1.1")).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(PasswordHash.class))).thenReturn(false);

        LoginResult result = loginHandler.handle(validCommand);

        assertFalse(result.isSuccess());
        assertEquals("INVALID_CREDENTIALS", result.getErrorCode());
    }

    @Test
    void testLoginWithUnknownUser() {
        when(rateLimiter.checkLimit("login:192.168.1.1")).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.empty());

        LoginResult result = loginHandler.handle(validCommand);

        assertFalse(result.isSuccess());
        assertEquals("INVALID_CREDENTIALS", result.getErrorCode());
    }

    @Test
    void testLoginRateLimited() {
        when(rateLimiter.checkLimit("login:192.168.1.1")).thenReturn(createDeniedResult("Too many attempts from this IP"));

        LoginResult result = loginHandler.handle(validCommand);

        assertFalse(result.isSuccess());
        assertEquals("RATE_LIMITED", result.getErrorCode());
    }

    @Test
    void testLoginWithInvalidUsername() {
        // LoginCommand validates at construction time - empty username rejected
        assertThrows(IllegalArgumentException.class, 
            () -> Username.of(""));
    }

    @Test
    void testLoginWithNullCommand() {
        assertThrows(NullPointerException.class, () -> loginHandler.handle(null));
    }

    @Test
    void testDifferentIPAddressesTrackSeparately() {
        LoginCommand cmd2 = new LoginCommand(Username.of("john_doe"), new Password("password123".toCharArray()), IpAddress.of("192.168.1.2"));

        when(rateLimiter.checkLimit(org.mockito.ArgumentMatchers.anyString())).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(PasswordHash.class))).thenReturn(true);
        when(clock.now()).thenReturn(Instant.now());

        LoginResult result1 = loginHandler.handle(validCommand);
        LoginResult result2 = loginHandler.handle(cmd2);

        assertTrue(result1.isSuccess());
        assertTrue(result2.isSuccess());
        
        verify(rateLimiter).checkLimit("login:192.168.1.1");
        verify(rateLimiter).checkLimit("login:192.168.1.2");
    }

    @Test
    void testRepositoryExceptionHandled() {
        when(rateLimiter.checkLimit("login:192.168.1.1")).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class)))
            .thenThrow(new RuntimeException("Database connection failed"));

        LoginResult result = loginHandler.handle(validCommand);

        assertFalse(result.isSuccess());
        assertEquals("INTERNAL_ERROR", result.getErrorCode());
    }

    @Test
    void testPasswordHasherExceptionHandled() {
        when(rateLimiter.checkLimit("login:192.168.1.1")).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(PasswordHash.class)))
            .thenThrow(new RuntimeException("Bcrypt error"));

        LoginResult result = loginHandler.handle(validCommand);

        assertFalse(result.isSuccess());
        assertEquals("INTERNAL_ERROR", result.getErrorCode());
    }

    @Test
    void testCompleteFlowWithAdminUser() {
        User adminUser = testUser.grantPermission(Permission.of("manage_users"));

        when(rateLimiter.checkLimit("login:192.168.1.1")).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(adminUser));
        when(passwordHasher.verify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(PasswordHash.class))).thenReturn(true);
        when(clock.now()).thenReturn(Instant.now());

        LoginResult result = loginHandler.handle(validCommand);

        assertTrue(result.isSuccess());
        assertNotNull(result.getAccessToken());
        assertNotNull(result.getRefreshToken());
    }

    @Test
    void testLoginCommandViaHandler() {
        char[] password = "mypass".toCharArray();
        LoginCommand cmd = new LoginCommand(Username.of("test_user"), new Password(password), IpAddress.of("10.0.0.1"));

        when(rateLimiter.checkLimit("login:10.0.0.1")).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(PasswordHash.class))).thenReturn(true);
        when(clock.now()).thenReturn(Instant.now());

        LoginResult result = loginHandler.handle(cmd);

        assertTrue(result.isSuccess());
    }

    @Test
    void testMultipleLoginAttemptsWithRateLimiting() {
        when(rateLimiter.checkLimit("login:192.168.1.1"))
            .thenReturn(createAllowedResult())
            .thenReturn(createAllowedResult())
            .thenReturn(createDeniedResult("Rate limited"));

        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(PasswordHash.class))).thenReturn(true);
        when(clock.now()).thenReturn(Instant.now());

        LoginResult result1 = loginHandler.handle(validCommand);
        assertTrue(result1.isSuccess());

        LoginResult result2 = loginHandler.handle(validCommand);
        assertTrue(result2.isSuccess());

        LoginResult result3 = loginHandler.handle(validCommand);
        assertFalse(result3.isSuccess());
        assertEquals("RATE_LIMITED", result3.getErrorCode());
    }

    @Test
    void testErrorMessagesAreSafe() {
        when(rateLimiter.checkLimit("login:192.168.1.1")).thenReturn(createAllowedResult());
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(testUser));

        LoginResult result = loginHandler.handle(validCommand);

        assertFalse(result.isSuccess());
        String errorMsg = result.getErrorMessage();
        assertFalse(errorMsg.contains("Optional.empty"));
        assertFalse(errorMsg.contains("null"));
        assertTrue(errorMsg.contains("Invalid username or password"));
    }

    /**
     * Helper to create a RateLimitResult for testing.
     */
    private Ports.RateLimitResult createAllowedResult() {
        return new TestRateLimitResult(true, Optional.empty());
    }

    /**
     * Helper to create a denied RateLimitResult for testing.
     */
    private Ports.RateLimitResult createDeniedResult(String message) {
        return new TestRateLimitResult(false, Optional.of(message));
    }

    /**
     * Test implementation of RateLimitResult.
     */
    private static class TestRateLimitResult implements Ports.RateLimitResult {
        private final boolean allowed;
        private final Optional<String> errorMessage;

        TestRateLimitResult(final boolean allowed, final Optional<String> errorMessage) {
            this.allowed = allowed;
            this.errorMessage = errorMessage;
        }

        @Override
        public boolean isAllowed() {
            return allowed;
        }

        @Override
        public Optional<String> getErrorMessage() {
            return errorMessage;
        }
    }
}
