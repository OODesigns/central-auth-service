package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.repository.UserRepository;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.*;
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
    private UserRepository userRepository;

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
        loginHandler = new LoginCommandHandler(userRepository, authService, rateLimiter);

        // Create test user
        UserId userId = UserId.generate();
        Username username = new Username("john_doe");
        PasswordHash passwordHash = new PasswordHash("$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
        testUser = User.create(userId, username, passwordHash);

        // Create valid command
        validCommand = new LoginCommand("john_doe", "password123".toCharArray(), "192.168.1.1");
    }

    @Test
    void testSuccessfulLogin() {
        doNothing().when(rateLimiter).checkLimit("login:192.168.1.1");
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
        doNothing().when(rateLimiter).checkLimit("login:192.168.1.1");
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(PasswordHash.class))).thenReturn(false);

        LoginResult result = loginHandler.handle(validCommand);

        assertFalse(result.isSuccess());
        assertEquals("INVALID_CREDENTIALS", result.getErrorCode());
    }

    @Test
    void testLoginWithUnknownUser() {
        doNothing().when(rateLimiter).checkLimit("login:192.168.1.1");
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.empty());

        LoginResult result = loginHandler.handle(validCommand);

        assertFalse(result.isSuccess());
        assertEquals("INVALID_CREDENTIALS", result.getErrorCode());
    }

    @Test
    void testLoginRateLimited() {
        doThrow(new Ports.RateLimitExceededException("Too many attempts from this IP"))
            .when(rateLimiter).checkLimit("login:192.168.1.1");

        LoginResult result = loginHandler.handle(validCommand);

        assertFalse(result.isSuccess());
        assertEquals("RATE_LIMITED", result.getErrorCode());
    }

    @Test
    void testLoginWithInvalidUsername() {
        // LoginCommand validates at construction time - empty username rejected
        char[] password = "password".toCharArray();
        assertThrows(IllegalArgumentException.class, () -> new LoginCommand("", password, "192.168.1.1"));
    }

    @Test
    void testLoginWithNullCommand() {
        assertThrows(NullPointerException.class, () -> loginHandler.handle(null));
    }

    @Test
    void testDifferentIPAddressesTrackSeparately() {
        LoginCommand cmd2 = new LoginCommand("john_doe", "password123".toCharArray(), "192.168.1.2");

        doNothing().when(rateLimiter).checkLimit(org.mockito.ArgumentMatchers.anyString());
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
        doNothing().when(rateLimiter).checkLimit("login:192.168.1.1");
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class)))
            .thenThrow(new RuntimeException("Database connection failed"));

        LoginResult result = loginHandler.handle(validCommand);

        assertFalse(result.isSuccess());
        assertEquals("INTERNAL_ERROR", result.getErrorCode());
    }

    @Test
    void testPasswordHasherExceptionHandled() {
        doNothing().when(rateLimiter).checkLimit("login:192.168.1.1");
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

        doNothing().when(rateLimiter).checkLimit("login:192.168.1.1");
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
        LoginCommand cmd = new LoginCommand("test_user", password, "10.0.0.1");

        doNothing().when(rateLimiter).checkLimit("login:10.0.0.1");
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.of(testUser));
        when(passwordHasher.verify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(PasswordHash.class))).thenReturn(true);
        when(clock.now()).thenReturn(Instant.now());

        LoginResult result = loginHandler.handle(cmd);

        assertTrue(result.isSuccess());
    }

    @Test
    void testMultipleLoginAttemptsWithRateLimiting() {
        doNothing()
            .doNothing()
            .doThrow(new Ports.RateLimitExceededException("Rate limited"))
            .when(rateLimiter).checkLimit("login:192.168.1.1");

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
        doNothing().when(rateLimiter).checkLimit("login:192.168.1.1");
        when(userRepository.findByUsername(org.mockito.ArgumentMatchers.any(Username.class))).thenReturn(Optional.empty());

        LoginResult result = loginHandler.handle(validCommand);

        assertFalse(result.isSuccess());
        String errorMsg = result.getErrorMessage();
        assertFalse(errorMsg.contains("Optional.empty"));
        assertFalse(errorMsg.contains("null"));
        assertTrue(errorMsg.contains("Invalid username or password"));
    }
}
