package com.oodesigns.cas.integration;

import com.oodesigns.cas.application.command.*;
import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.value.*;
import com.oodesigns.cas.domain.value.IpAddress;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.Username;
import com.oodesigns.cas.infrastructure.adapter.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the login command handler using mock adapters.
 * Tests general login scenarios (not admin-specific) with in-memory mocks:
 * - Valid credentials login
 * - Invalid password handling
 * - Non-existent user handling
 * - Multiple users in system
 * - Rate limiting behavior
 * - Token generation
 * Uses mock adapters (InMemoryUserRepository, MockPasswordVerifier, MockClock, etc.)
 * for fast unit-like testing without external dependencies.
 * For admin-specific scenarios with mocks, see: AdminLoginMockIntegrationTest
 * For real database testing, see: AdminLoginDatabaseIntegrationTest
 */
class LoginMockIntegrationTest {

    private LoginCommandHandler loginHandler;
    private InMemoryUserRepository userRepository;
    private MockPasswordVerifier passwordHasher;
    private MockClock clock;
    private Bucket4jRateLimiter rateLimiter;
    private MockTokenSigner tokenSigner;

    @BeforeEach
    void setUp() {
        // Initialize all adapters
        userRepository = new InMemoryUserRepository();
        passwordHasher = new MockPasswordVerifier();
        clock = new MockClock(Instant.now());
        rateLimiter = new Bucket4jRateLimiter(5, java.time.Duration.ofMinutes(1)); // Allow 5 attempts per minute per IP
        tokenSigner = new MockTokenSigner();

        // Create domain services with injected ports
        AuthenticationService authService = new AuthenticationService(passwordHasher);
        TokenService tokenService = new TokenService(clock, tokenSigner);

        // Create command handler with injected dependencies
        // InMemoryUserRepository implements both UserCredentialReader and UserRepository
        loginHandler = new LoginCommandHandler(authService, tokenService, userRepository, userRepository, rateLimiter);
    }

    /**
     * Test helper: Setup a user with credentials for authentication.
     * Saves both the User (for post-auth) and UserCredential (for authentication).
     */
    private void setupUserWithCredentials(UserId userId, Username username, String password) {
        User user = new User(userId, username, Set.of());
        PasswordHash passwordHash = passwordHasher.hash(password.toCharArray());
        userRepository.save(user);
        userRepository.saveCredential(new UserCredential(userId, passwordHash));
    }

    @Test
    void testCompleteLoginFlow() {
        // 1. Setup: Create and persist a user
        UserId userId = UserId.generate();
        Username username = new Username("alice_smith");
        setupUserWithCredentials(userId, username, "correct_password");

        // 2. Execute: Login command
        LoginCommand loginCmd = new LoginCommand(Username.of("alice_smith"), new Password("correct_password".toCharArray()), 
            IpAddress.of("192.168.1.100"));
        
        LoginResult result = loginHandler.handle(loginCmd);

        // 3. Verify: Successful login using fluent mapTo/orElse
        result.mapTo(success -> {
                assertNotNull(success.tokenPair());
                assertNotNull(success.tokenPair().accessToken());
                assertNotNull(success.tokenPair().refreshToken());
                return null;
            })
            .orElse(ignored -> {
                fail("Login should have succeeded");
                return null;
            });
    }

    @Test
    void testLoginWithInvalidPassword() {
        // 1. Setup: Create user with specific password
        UserId userId = UserId.generate();
        Username username = new Username("bob_jones");
        setupUserWithCredentials(userId, username, "correct_password");

        // 2. Execute: Wrong password
        LoginCommand loginCmd = new LoginCommand(Username.of("bob_jones"), new Password("wrong_password".toCharArray()), 
            IpAddress.of("192.168.1.101"));
        
        LoginResult result = loginHandler.handle(loginCmd);

        // 3. Verify: Failed login with generic error
        result.mapTo(ignored -> {
                fail("Login should have failed");
                return null;
            })
            .orElse(err -> {
                assertEquals("INVALID_CREDENTIALS", err.errorCode());
                assertTrue(err.errorMessage().contains("Invalid username or password"));
                return null;
            });
    }

    @Test
    void testLoginWithNonExistentUser() {
        // 1. Setup: No users in repository

        // 2. Execute: Login for non-existent user
        LoginCommand loginCmd = new LoginCommand(Username.of("nonexistent"), new Password("password".toCharArray()), 
            IpAddress.of("192.168.1.102"));
        
        LoginResult result = loginHandler.handle(loginCmd);

        // 3. Verify: Generic error (no "user not found")
        result.mapTo(ignored -> {
                fail("Login should have failed");
                return null;
            })
            .orElse(err -> {
                assertEquals("INVALID_CREDENTIALS", err.errorCode());
                return null;
            });
    }

    @Test
    void testMultipleUsersInSystem() {
        // 1. Setup: Create multiple users with credentials
        UserId aliceId = UserId.generate();
        UserId bobId = UserId.generate();
        UserId charlieId = UserId.generate();
        
        setupUserWithCredentials(aliceId, new Username("alice"), "correct_password");
        setupUserWithCredentials(bobId, new Username("bob"), "correct_password");
        setupUserWithCredentials(charlieId, new Username("charlie"), "correct_password");

        // 2. Execute: Login as each user
        LoginCommand aliceCmd = new LoginCommand(Username.of("alice"), new Password("correct_password".toCharArray()), 
            IpAddress.of("192.168.1.100"));
        LoginCommand bobCmd = new LoginCommand(Username.of("bob"), new Password("correct_password".toCharArray()), 
            IpAddress.of("192.168.1.101"));
        LoginCommand charlieCmd = new LoginCommand(Username.of("charlie"), new Password("correct_password".toCharArray()), 
            IpAddress.of("192.168.1.102"));

        LoginResult aliceResult = loginHandler.handle(aliceCmd);
        LoginResult bobResult = loginHandler.handle(bobCmd);
        LoginResult charlieResult = loginHandler.handle(charlieCmd);

        // 3. Verify: All users can login
        aliceResult.mapTo(success -> {
                assertNotNull(success.tokenPair());
                return null;
            })
            .orElse(ignored -> {
                fail("Alice login should have succeeded");
                return null;
            });

        bobResult.mapTo(success -> {
                assertNotNull(success.tokenPair());
                return null;
            })
            .orElse(ignored -> {
                fail("Bob login should have succeeded");
                return null;
            });

        charlieResult.mapTo(success -> {
                assertNotNull(success.tokenPair());
                return null;
            })
            .orElse(ignored -> {
                fail("Charlie login should have succeeded");
                return null;
            });
    }

    @Test
    void testRateLimitingPerIPAddress() {
        // 1. Setup: User
        UserId userId = UserId.generate();
        setupUserWithCredentials(userId, new Username("rate_test"), "correct_password");

        // 2. Execute: Multiple login attempts from same IP
        String testIP = "10.0.0.5";
        for (int i = 0; i < 5; i++) {
            LoginCommand cmd = new LoginCommand(Username.of("rate_test"), new Password("correct_password".toCharArray()), 
                IpAddress.of(testIP));
            LoginResult result = loginHandler.handle(cmd);
            final int attempt = i + 1;
            result.mapTo(success -> {
                    assertNotNull(success.tokenPair());
                    return null;
                })
                .orElse(ignored -> {
                    fail("Attempt " + attempt + " should succeed");
                    return null;
                });
        }

        // 3. Verify: 6th attempt is rate limited
        LoginCommand blockedCmd = new LoginCommand(Username.of("rate_test"), new Password("correct_password".toCharArray()), 
            IpAddress.of(testIP));
        LoginResult blockedResult = loginHandler.handle(blockedCmd);
        
        blockedResult.mapTo(ignored -> {
                fail("Should have been rate limited");
                return null;
            })
            .orElse(err -> {
                assertEquals("RATE_LIMITED", err.errorCode());
                return null;
            });
    }

    @Test
    void testDifferentIPsNotRateLimited() {
        // 1. Setup: User
        UserId userId = UserId.generate();
        setupUserWithCredentials(userId, new Username("multi_ip_test"), "correct_password");

        // 2. Execute: Attempts from different IPs should succeed independently
        String[] ips = {"10.0.0.1", "10.0.0.2", "10.0.0.3"};
        for (String ip : ips) {
            LoginCommand cmd = new LoginCommand(Username.of("multi_ip_test"), new Password("correct_password".toCharArray()), 
                IpAddress.of(ip));
            LoginResult result = loginHandler.handle(cmd);
            result.mapTo(success -> {
                    assertNotNull(success.tokenPair());
                    return null;
                })
                .orElse(ignored -> {
                    fail("Login from IP " + ip + " should succeed");
                    return null;
                });
        }
    }

    @Test
    void testUserWithMultiplePermissions() {
        // 1. Setup: User with multiple permissions
        UserId userId = UserId.generate();
        setupUserWithCredentials(userId, new Username("super_admin"), "correct_password");
        User user = new User(userId, new Username("super_admin"), Set.of(
            Permission.of("manage_users"),
            Permission.of("delete_accounts")
        ));
        userRepository.save(user);

        // 2. Verify: User has both permissions
        assertTrue(user.permissions().contains(Permission.of("manage_users")));
        assertTrue(user.permissions().contains(Permission.of("delete_accounts")));

        // 3. Execute: Login should work
        LoginCommand cmd = new LoginCommand(Username.of("super_admin"), new Password("correct_password".toCharArray()), 
            IpAddress.of("192.168.1.100"));
        LoginResult result = loginHandler.handle(cmd);

        result.mapTo(success -> {
                assertNotNull(success.tokenPair());
                return null;
            })
            .orElse(ignored -> {
                fail("Login should have succeeded");
                return null;
            });
    }

    @Test
    void testImmutabilityOfUserAfterLogin() {
        // 1. Setup: Create and persist user
        UserId userId = UserId.generate();
        setupUserWithCredentials(userId, new Username("immutable_test"), "correct_password");
        User originalUser = new User(userId, new Username("immutable_test"), Set.of());
        userRepository.save(originalUser);

        // 2. Execute: Login (which uses the user from repository)
        LoginCommand cmd = new LoginCommand(Username.of("immutable_test"), new Password("correct_password".toCharArray()), 
            IpAddress.of("192.168.1.100"));
        LoginResult result = loginHandler.handle(cmd);

        // 3. Verify: Original user object unchanged
        result.mapTo(ignored -> {
                assertTrue(originalUser.permissions().isEmpty());
                return null;
            })
            .orElse(error -> {
                fail("Login should have succeeded");
                return null;
            });
    }

    @Test
    void testPasswordSecurityWithCharArrays() {
        // 1. Setup: User
        UserId userId = UserId.generate();
        setupUserWithCredentials(userId, new Username("security_test"), "correct_password");

        // 2. Execute: Login with char array password
        char[] password = "correct_password".toCharArray();
        LoginCommand cmd = new LoginCommand(Username.of("security_test"), new Password(password), 
            IpAddress.of("192.168.1.100"));
        
        // Clear the original char array after command creation
        Arrays.fill(password, '\0');

        // 3. Verify: Command still has the correct password (cloned)
        LoginResult result = loginHandler.handle(cmd);
        result.mapTo(success -> {
                assertNotNull(success.tokenPair());
                return null;
            })
            .orElse(ignored -> {
                fail("Login should have succeeded");
                return null;
            });
    }

    @Test
    void testValueObjectConsistency() {
        // 1. Setup: Create users with specific value objects
        Username username = new Username("VALUE_OBJECT_TEST");
        UserId userId = UserId.generate();
        
        setupUserWithCredentials(userId, username, "correct_password");
        User user = new User(userId, username, Set.of());
        userRepository.save(user);

        // 2. Execute: Login with normalized username
        LoginCommand cmd = new LoginCommand(Username.of("value_object_test"), new Password("correct_password".toCharArray()), 
            IpAddress.of("192.168.1.100"));
        LoginResult result = loginHandler.handle(cmd);

        // 3. Verify: Username normalization works (case-insensitive matching)
        result.mapTo(success -> {
                assertNotNull(success.tokenPair());
                return null;
            })
            .orElse(ignored -> {
                fail("Login should have succeeded");
                return null;
            });
    }

    @Test
    void testClockAdvancement() {
        // 1. Setup: User with mock clock
        UserId userId = UserId.generate();
        setupUserWithCredentials(userId, new Username("clock_test"), "correct_password");

        Instant t1 = clock.now();

        // 2. Execute: First login
        LoginCommand cmd1 = new LoginCommand(Username.of("clock_test"), new Password("correct_password".toCharArray()), 
            IpAddress.of("192.168.1.100"));
        LoginResult result1 = loginHandler.handle(cmd1);

        // 3. Advance clock
        clock.advanceSeconds(60);
        Instant t2 = clock.now();

        // 4. Execute: Second login
        LoginCommand cmd2 = new LoginCommand(Username.of("clock_test"), new Password("correct_password".toCharArray()), 
            IpAddress.of("192.168.1.101"));
        LoginResult result2 = loginHandler.handle(cmd2);

        // 5. Verify: Both successful, clock advanced
        result1.mapTo(success -> {
                assertNotNull(success.tokenPair());
                return null;
            })
            .orElse(ignored -> {
                fail("First login should have succeeded");
                return null;
            });

        result2.mapTo(success -> {
                assertNotNull(success.tokenPair());
                return null;
            })
            .orElse(ignored -> {
                fail("Second login should have succeeded");
                return null;
            });

        assertTrue(t2.isAfter(t1));
    }

    @Test
    void testRepositoryPersistence() {
        // 1. Setup: Save multiple users with credentials
        UserId user1Id = UserId.generate();
        UserId user2Id = UserId.generate();
        setupUserWithCredentials(user1Id, new Username("persist1"), "password1");
        setupUserWithCredentials(user2Id, new Username("persist2"), "password2");

        // 2. Verify: Repository persists users
        assertEquals(2, userRepository.size());
        assertTrue(userRepository.findCredentialsByUsername(new Username("persist1")).isPresent());
        assertTrue(userRepository.findCredentialsByUsername(new Username("persist2")).isPresent());
    }

    @Test
    void testEndToEndSecurityFlow() {
        // Complete realistic scenario:
        // 1. User registration
        UserId userId = UserId.generate();
        Username username = new Username("secure_user");
        setupUserWithCredentials(userId, username, "MySecurePassword123");

        // 2. Successful login
        LoginCommand correctCmd = new LoginCommand(Username.of("secure_user"), new Password("MySecurePassword123".toCharArray()), 
            IpAddress.of("192.168.1.50"));
        LoginResult correctResult = loginHandler.handle(correctCmd);
        correctResult.mapTo(success -> {
                assertNotNull(success.tokenPair());
                return null;
            })
            .orElse(ignored -> {
                fail("Correct login should have succeeded");
                return null;
            });

        // 3. Failed login attempt
        LoginCommand wrongCmd = new LoginCommand(Username.of("secure_user"), new Password("WrongPassword".toCharArray()), 
            IpAddress.of("192.168.1.50"));
        LoginResult wrongResult = loginHandler.handle(wrongCmd);
        wrongResult.mapTo(ignored -> {
                fail("Wrong password login should have failed");
                return null;
            })
            .orElse(err -> {
                assertEquals("INVALID_CREDENTIALS", err.errorCode());
                return null;
            });

        // 4. Verify rate limit not hit (different IP, new user)
        LoginCommand anotherUserCmd = new LoginCommand(Username.of("secure_user"), new Password("MySecurePassword123".toCharArray()), 
            IpAddress.of("192.168.1.51"));
        LoginResult anotherResult = loginHandler.handle(anotherUserCmd);
        anotherResult.mapTo(success -> {
                assertNotNull(success.tokenPair());
                return null;
            })
            .orElse(ignored -> {
                fail("Another login should have succeeded");
                return null;
            });
    }

    @Test
    void testLoginResponseReturnsTokens() {
        // 1. Setup: Create user with permissions
        UserId userId = UserId.generate();
        Username username = new Username("powerful_user");
        setupUserWithCredentials(userId, username, "super_secret");
        
        User user = new User(userId, username, Set.of(
            com.oodesigns.cas.domain.value.Permission.of("manage_users"),
            com.oodesigns.cas.domain.value.Permission.of("view_reports"),
            com.oodesigns.cas.domain.value.Permission.of("delete_accounts")
        ));
        
        userRepository.save(user);

        // 2. Execute: Login
        LoginCommand loginCmd = new LoginCommand(Username.of("powerful_user"), new Password("super_secret".toCharArray()), 
            IpAddress.of("192.168.1.99"));
        
        LoginResult result = loginHandler.handle(loginCmd);

        // 3. Verify: Token pair returned in response
        result.mapTo(success -> {
                assertNotNull(success.tokenPair());
                assertNotNull(success.tokenPair().accessToken());
                assertNotNull(success.tokenPair().refreshToken());
                return null;
            })
            .orElse(ignored -> {
                fail("Login should have succeeded");
                return null;
            });
    }

    @Test
    void testLoginWithNoPermissionsStillReturnsTokens() {
        // 1. Setup: Create user with no permissions
        UserId userId = UserId.generate();
        Username username = new Username("basic_user");
        setupUserWithCredentials(userId, username, "basic_pass");

        // 2. Execute: Login
        LoginCommand loginCmd = new LoginCommand(Username.of("basic_user"), new Password("basic_pass".toCharArray()), 
            IpAddress.of("192.168.1.88"));
        
        LoginResult result = loginHandler.handle(loginCmd);

        // 3. Verify: Token pair still returned
        result.mapTo(success -> {
            assertNotNull(success.tokenPair());
            assertNotNull(success.tokenPair().accessToken());
            assertNotNull(success.tokenPair().refreshToken());
                return null;
            })
            .orElse(ignored -> {
                fail("Login should have succeeded");
                return null;
            });
    }
}
