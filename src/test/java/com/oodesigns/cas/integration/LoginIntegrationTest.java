package com.oodesigns.cas.integration;

import com.oodesigns.cas.application.command.*;
import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.value.*;
import com.oodesigns.cas.infrastructure.adapter.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the login use case.
 * 
 * Demonstrates the complete hexagonal architecture flow:
 *   REST Request (LoginRequestDto)
 *   ↓
 *   Application Command (LoginCommand)
 *   ↓
 *   Command Handler (LoginCommandHandler)
 *   ↓
 *   Domain Services (AuthenticationService)
 *   ↓
 *   Value Objects & Entities (User, UserId, Username, PasswordHash, Role)
 *   ↓
 *   Ports (UserRepository, PasswordHasher, Clock, RateLimiter)
 *   ↓
 *   Adapters (InMemoryUserRepository, MockPasswordHasher, MockClock, MockRateLimiter)
 *   ↓
 *   Result (LoginResult)
 * 
 * All external dependencies are injected as ports; adapters handle specifics.
 */
public class LoginIntegrationTest {

    private LoginCommandHandler loginHandler;
    private InMemoryUserRepository userRepository;
    private MockPasswordHasher passwordHasher;
    private MockClock clock;
    private MockRateLimiter rateLimiter;

    @BeforeEach
    public void setUp() {
        // Initialize all adapters
        userRepository = new InMemoryUserRepository();
        passwordHasher = new MockPasswordHasher();
        clock = new MockClock(Instant.now());
        rateLimiter = new MockRateLimiter(5); // Allow 5 attempts per IP

        // Create domain service with injected ports
        AuthenticationService authService = new AuthenticationService(passwordHasher, clock);

        // Create command handler with injected dependencies
        loginHandler = new LoginCommandHandler(userRepository, authService, rateLimiter);
    }

    @Test
    public void testCompleteLoginFlow() {
        // 1. Setup: Create and persist a user
        UserId userId = UserId.generate();
        Username username = new Username("alice_smith");
        PasswordHash passwordHash = passwordHasher.hash("correct_password");
        User user = User.create(userId, username, passwordHash)
            .assignRole(Role.user());
        userRepository.save(user);

        // 2. Execute: Login command
        LoginCommand loginCmd = new LoginCommand("alice_smith", "correct_password".toCharArray(), 
            "192.168.1.100");
        
        LoginResult result = loginHandler.handle(loginCmd);

        // 3. Verify: Successful login
        assertTrue(result.isSuccess());
        assertNotNull(result.getAccessToken());
        assertNotNull(result.getRefreshToken());
    }

    @Test
    public void testLoginWithInvalidPassword() {
        // 1. Setup: Create user with specific password
        UserId userId = UserId.generate();
        Username username = new Username("bob_jones");
        PasswordHash passwordHash = passwordHasher.hash("correct_password");
        User user = User.create(userId, username, passwordHash);
        userRepository.save(user);

        // 2. Execute: Wrong password
        LoginCommand loginCmd = new LoginCommand("bob_jones", "wrong_password".toCharArray(), 
            "192.168.1.101");
        
        LoginResult result = loginHandler.handle(loginCmd);

        // 3. Verify: Failed login with generic error
        assertFalse(result.isSuccess());
        assertEquals("INVALID_CREDENTIALS", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("Invalid username or password"));
    }

    @Test
    public void testLoginWithNonExistentUser() {
        // 1. Setup: No users in repository

        // 2. Execute: Login for non-existent user
        LoginCommand loginCmd = new LoginCommand("nonexistent", "password".toCharArray(), 
            "192.168.1.102");
        
        LoginResult result = loginHandler.handle(loginCmd);

        // 3. Verify: Generic error (no "user not found")
        assertFalse(result.isSuccess());
        assertEquals("INVALID_CREDENTIALS", result.getErrorCode());
    }

    @Test
    public void testMultipleUsersInSystem() {
        // 1. Setup: Create multiple users
        User alice = User.create(UserId.generate(), new Username("alice"), 
            passwordHasher.hash("correct_password")).assignRole(Role.user());
        User bob = User.create(UserId.generate(), new Username("bob"), 
            passwordHasher.hash("correct_password")).assignRole(Role.admin());
        User charlie = User.create(UserId.generate(), new Username("charlie"), 
            passwordHasher.hash("correct_password")).assignRole(Role.kiosk());

        userRepository.save(alice);
        userRepository.save(bob);
        userRepository.save(charlie);

        // 2. Execute: Login as each user
        LoginCommand aliceCmd = new LoginCommand("alice", "correct_password".toCharArray(), 
            "192.168.1.100", "Mozilla");
        LoginCommand bobCmd = new LoginCommand("bob", "correct_password".toCharArray(), 
            "192.168.1.101", "Chrome");
        LoginCommand charlieCmd = new LoginCommand("charlie", "correct_password".toCharArray(), 
            "192.168.1.102");

        LoginResult aliceResult = loginHandler.handle(aliceCmd);
        LoginResult bobResult = loginHandler.handle(bobCmd);
        LoginResult charlieResult = loginHandler.handle(charlieCmd);

        // 3. Verify: All users can login
        assertTrue(aliceResult.isSuccess());
        assertTrue(bobResult.isSuccess());
        assertTrue(charlieResult.isSuccess());
    }

    @Test
    public void testRateLimitingPerIPAddress() {
        // 1. Setup: User
        User user = User.create(UserId.generate(), new Username("rate_test"), 
            passwordHasher.hash("correct_password"));
        userRepository.save(user);

        // 2. Execute: Multiple login attempts from same IP
        String testIP = "10.0.0.5";
        for (int i = 0; i < 5; i++) {
            LoginCommand cmd = new LoginCommand("rate_test", "correct_password".toCharArray(), 
                testIP, "Mozilla");
            LoginResult result = loginHandler.handle(cmd);
            assertTrue(result.isSuccess(), "Attempt " + (i+1) + " should succeed");
        }

        // 3. Verify: 6th attempt is rate limited
        LoginCommand blockedCmd = new LoginCommand("rate_test", "correct_password".toCharArray(), 
            testIP, "Mozilla");
        LoginResult blockedResult = loginHandler.handle(blockedCmd);
        
        assertFalse(blockedResult.isSuccess());
        assertEquals("RATE_LIMITED", blockedResult.getErrorCode());
    }

    @Test
    public void testDifferentIPsNotRateLimited() {
        // 1. Setup: User
        User user = User.create(UserId.generate(), new Username("multi_ip_test"), 
            passwordHasher.hash("correct_password"));
        userRepository.save(user);

        // 2. Execute: Attempts from different IPs should succeed independently
        String[] ips = {"10.0.0.1", "10.0.0.2", "10.0.0.3"};
        for (String ip : ips) {
            LoginCommand cmd = new LoginCommand("multi_ip_test", "correct_password".toCharArray(), 
                ip, "Mozilla");
            LoginResult result = loginHandler.handle(cmd);
            assertTrue(result.isSuccess(), "Login from IP " + ip + " should succeed");
        }
    }

    @Test
    public void testUserWithMultipleRoles() {
        // 1. Setup: User with admin and user roles
        UserId userId = UserId.generate();
        User user = User.create(userId, new Username("super_admin"), 
            passwordHasher.hash("correct_password"))
            .assignRole(Role.admin())
            .assignRole(Role.user());
        userRepository.save(user);

        // 2. Verify: User has both roles
        assertTrue(user.hasRole(Role.admin()));
        assertTrue(user.hasRole(Role.user()));
        assertTrue(user.isAdmin());

        // 3. Execute: Login should work
        LoginCommand cmd = new LoginCommand("super_admin", "correct_password".toCharArray(), 
            "192.168.1.100", "Mozilla");
        LoginResult result = loginHandler.handle(cmd);

        assertTrue(result.isSuccess());
    }

    @Test
    public void testImmutabilityOfUserAfterLogin() {
        // 1. Setup: Create and persist user
        UserId userId = UserId.generate();
        User originalUser = User.create(userId, new Username("immutable_test"), 
            passwordHasher.hash("correct_password")).assignRole(Role.user());
        userRepository.save(originalUser);

        // 2. Execute: Login (which uses the user from repository)
        LoginCommand cmd = new LoginCommand("immutable_test", "correct_password".toCharArray(), 
            "192.168.1.100", "Mozilla");
        LoginResult result = loginHandler.handle(cmd);

        // 3. Verify: Original user object unchanged
        assertTrue(result.isSuccess());
        assertEquals(1, originalUser.getRoles().size());
        assertTrue(originalUser.hasRole(Role.user()));
        assertFalse(originalUser.hasRole(Role.admin()));
    }

    @Test
    public void testPasswordSecurityWithCharArrays() {
        // 1. Setup: User
        User user = User.create(UserId.generate(), new Username("security_test"), 
            passwordHasher.hash("correct_password"));
        userRepository.save(user);

        // 2. Execute: Login with char array password
        char[] password = "correct_password".toCharArray();
        LoginCommand cmd = new LoginCommand("security_test", password, 
            "192.168.1.100", "Mozilla");
        
        // Clear the original char array after command creation
        for (int i = 0; i < password.length; i++) {
            password[i] = '\0';
        }

        // 3. Verify: Command still has the correct password (cloned)
        LoginResult result = loginHandler.handle(cmd);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testValueObjectConsistency() {
        // 1. Setup: Create users with specific value objects
        Username username = new Username("VALUE_OBJECT_TEST");
        UserId userId = UserId.generate();
        PasswordHash hash = passwordHasher.hash("correct_password");

        User user = User.create(userId, username, hash);
        userRepository.save(user);

        // 2. Execute: Login with normalized username
        LoginCommand cmd = new LoginCommand("value_object_test", "correct_password".toCharArray(), 
            "192.168.1.100", "Mozilla");
        LoginResult result = loginHandler.handle(cmd);

        // 3. Verify: Username normalization works (case-insensitive matching)
        assertTrue(result.isSuccess());
    }

    @Test
    public void testClockAdvancement() {
        // 1. Setup: User with mock clock
        User user = User.create(UserId.generate(), new Username("clock_test"), 
            passwordHasher.hash("correct_password"));
        userRepository.save(user);

        Instant t1 = clock.now();

        // 2. Execute: First login
        LoginCommand cmd1 = new LoginCommand("clock_test", "correct_password".toCharArray(), 
            "192.168.1.100", "Mozilla");
        LoginResult result1 = loginHandler.handle(cmd1);

        // 3. Advance clock
        clock.advanceSeconds(60);
        Instant t2 = clock.now();

        // 4. Execute: Second login
        LoginCommand cmd2 = new LoginCommand("clock_test", "correct_password".toCharArray(), 
            "192.168.1.101");
        LoginResult result2 = loginHandler.handle(cmd2);

        // 5. Verify: Both successful, clock advanced
        assertTrue(result1.isSuccess());
        assertTrue(result2.isSuccess());
        assertTrue(t2.isAfter(t1));
    }

    @Test
    public void testRepositoryPersistence() {
        // 1. Setup: Save multiple users
        User user1 = User.create(UserId.generate(), new Username("persist1"), 
            passwordHasher.hash("correct_password"));
        User user2 = User.create(UserId.generate(), new Username("persist2"), 
            passwordHasher.hash("correct_password"));

        userRepository.save(user1);
        userRepository.save(user2);

        // 2. Verify: Repository persists users
        assertEquals(2, userRepository.size());
        assertTrue(userRepository.findByUsername(new Username("persist1")).isPresent());
        assertTrue(userRepository.findByUsername(new Username("persist2")).isPresent());
    }

    @Test
    public void testEndToEndSecurityFlow() {
        // Complete realistic scenario:
        // 1. User registration
        UserId userId = UserId.generate();
        Username username = new Username("secure_user");
        PasswordHash hash = passwordHasher.hash("MySecurePassword123");
        User user = User.create(userId, username, hash)
            .assignRole(Role.user())
            .assignRole(Role.kiosk());
        userRepository.save(user);

        // 2. Successful login
        LoginCommand correctCmd = new LoginCommand("secure_user", "MySecurePassword123".toCharArray(), 
            "192.168.1.50");
        LoginResult correctResult = loginHandler.handle(correctCmd);
        assertTrue(correctResult.isSuccess());

        // 3. Failed login attempt
        LoginCommand wrongCmd = new LoginCommand("secure_user", "WrongPassword".toCharArray(), 
            "192.168.1.50");
        LoginResult wrongResult = loginHandler.handle(wrongCmd);
        assertFalse(wrongResult.isSuccess());

        // 4. Verify rate limit not hit (different IP, new user)
        LoginCommand anotherUserCmd = new LoginCommand("secure_user", "MySecurePassword123".toCharArray(), 
            "192.168.1.51");
        LoginResult anotherResult = loginHandler.handle(anotherUserCmd);
        assertTrue(anotherResult.isSuccess());
    }

    @Test
    public void testLoginResponseIncludesPermissions() {
        // 1. Setup: Create user with permissions
        UserId userId = UserId.generate();
        Username username = new Username("powerful_user");
        PasswordHash passwordHash = passwordHasher.hash("super_secret");
        
        User user = User.create(userId, username, passwordHash)
            .assignRole(Role.admin())
            .grantPermission(com.oodesigns.cas.domain.value.Permission.MANAGE_USERS())
            .grantPermission(com.oodesigns.cas.domain.value.Permission.VIEW_REPORTS())
            .grantPermission(com.oodesigns.cas.domain.value.Permission.DELETE_ACCOUNTS());
        
        userRepository.save(user);

        // 2. Execute: Login
        LoginCommand loginCmd = new LoginCommand("powerful_user", "super_secret".toCharArray(), 
            "192.168.1.99");
        
        LoginResult result = loginHandler.handle(loginCmd);

        // 3. Verify: Permissions returned in response
        assertTrue(result.isSuccess());
        assertEquals(3, result.getPermissions().size());
        assertTrue(result.getPermissions().contains(com.oodesigns.cas.domain.value.Permission.MANAGE_USERS()));
        assertTrue(result.getPermissions().contains(com.oodesigns.cas.domain.value.Permission.VIEW_REPORTS()));
        assertTrue(result.getPermissions().contains(com.oodesigns.cas.domain.value.Permission.DELETE_ACCOUNTS()));
    }

    @Test
    public void testLoginWithNoPermissions() {
        // 1. Setup: Create user with no permissions
        UserId userId = UserId.generate();
        Username username = new Username("basic_user");
        PasswordHash passwordHash = passwordHasher.hash("basic_pass");
        User user = User.create(userId, username, passwordHash)
            .assignRole(Role.user());
        userRepository.save(user);

        // 2. Execute: Login
        LoginCommand loginCmd = new LoginCommand("basic_user", "basic_pass".toCharArray(), 
            "192.168.1.88", "Chrome");
        
        LoginResult result = loginHandler.handle(loginCmd);

        // 3. Verify: Empty permissions set returned
        assertTrue(result.isSuccess());
        assertEquals(0, result.getPermissions().size());
    }
}
