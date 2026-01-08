package com.oodesigns.cas.integration;

import com.oodesigns.cas.application.command.*;
import com.oodesigns.cas.domain.entity.User;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.value.*;
import com.oodesigns.cas.infrastructure.adapter.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the login command handler using mock adapters.
 * ✅ NO DATABASE REQUIRED: Uses in-memory mocks for fast testing.
 * <p>
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
 * For real database testing, see: integration/database/AdminLoginDatabaseIntegrationTest
 */
@org.junit.jupiter.api.Tag("integration")
class LoginMockIntegrationTest {

    private static final String VALID_PASSWORD = "ValidPassword1234";  // 16 chars
    private static final String VALID_WRONG_PASSWORD = "WrongPassword1234";  // 16 chars

    private LoginCommandHandler loginHandler;
    private InMemoryUserRepository userRepository;
    private MockPasswordVerifier passwordHasher;
    private MockClock clock;

    @BeforeEach
    void setUp() {
        // Initialize all adapters
        userRepository = new InMemoryUserRepository();
        passwordHasher = new MockPasswordVerifier();
        clock = new MockClock(Instant.now());
        final Bucket4jRateLimiter rateLimiter = new Bucket4jRateLimiter(5, java.time.Duration.ofMinutes(1)); // Allow 5 attempts per minute per IP
        final MockTokenSigner tokenSigner = new MockTokenSigner();

        // Create domain services with injected ports
        final AuthenticationService authService = new AuthenticationService(passwordHasher);
        final TokenService tokenService = new TokenService(clock, tokenSigner);

        // Create command handler with injected dependencies
        // InMemoryUserRepository implements both UserCredentialReader and UserRepository
        loginHandler = new LoginCommandHandler(authService, tokenService, userRepository, userRepository, rateLimiter);
    }

    /**
     * Test helper: Set up a user with credentials for authentication.
     * Saves both the User (for post-auth) and UserCredential (for authentication).
     */
    private void setupUserWithCredentials(final UserId userId, final Username username) {
        final User user = new User(userId, username, Set.of());
        final PasswordHash passwordHash = passwordHasher.hash(LoginMockIntegrationTest.VALID_PASSWORD.toCharArray());
        userRepository.save(user);
        userRepository.saveCredential(UserCredential.of(userId, passwordHash));
    }

    @Test
    void testCompleteLoginFlow() {
        // 1. Setup: Create and persist a user
        final UserId userId = UserId.of(UUID.randomUUID());
        final Username username = Username.of("alice_smith");
        setupUserWithCredentials(userId, username);

        // 2. Execute: Login command
        final LoginCommand loginCmd = new LoginCommand(Username.of("alice_smith"), Password.of(VALID_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.100"));

        final LoginResult result = loginHandler.handle(loginCmd);

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
        final UserId userId = UserId.of(UUID.randomUUID());
        final Username username = Username.of("bob_jones");
        setupUserWithCredentials(userId, username);

        // 2. Execute: Wrong password
        final LoginCommand loginCmd = new LoginCommand(Username.of("bob_jones"), Password.of(VALID_WRONG_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.101"));

        final LoginResult result = loginHandler.handle(loginCmd);

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
        final LoginCommand loginCmd = new LoginCommand(Username.of("nonexistent"), Password.of(VALID_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.102"));

        final LoginResult result = loginHandler.handle(loginCmd);

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
        final UserId aliceId = UserId.of(UUID.randomUUID());
        final UserId bobId = UserId.of(UUID.randomUUID());
        final UserId charlieId = UserId.of(UUID.randomUUID());

        setupUserWithCredentials(aliceId, Username.of("alice"));
        setupUserWithCredentials(bobId, Username.of("bob"));
        setupUserWithCredentials(charlieId, Username.of("charlie"));

        // 2. Execute: Login as each user
        final LoginCommand aliceCmd = new LoginCommand(Username.of("alice"), Password.of(VALID_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.100"));
        final LoginCommand bobCmd = new LoginCommand(Username.of("bob"), Password.of(VALID_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.101"));
        final LoginCommand charlieCmd = new LoginCommand(Username.of("charlie"), Password.of(VALID_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.102"));

        final LoginResult aliceResult = loginHandler.handle(aliceCmd);
        final LoginResult bobResult = loginHandler.handle(bobCmd);
        final LoginResult charlieResult = loginHandler.handle(charlieCmd);

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
        final UserId userId = UserId.of(UUID.randomUUID());
        setupUserWithCredentials(userId, Username.of("rate_test"));

        // 2. Execute: Multiple login attempts from same IP
        final String testIP = "10.0.0.5";
        for (int i = 0; i < 5; i++) {
            final LoginCommand cmd = new LoginCommand(Username.of("rate_test"), Password.of(VALID_PASSWORD.toCharArray()),
                IpAddress.of(testIP));
            final LoginResult result = loginHandler.handle(cmd);
            final int attempt = i + 1;
            result.mapTo(success -> {
                    assertNotNull(success.tokenPair());
                    return null;
                })
                .orElse(ignored -> {
                    fail("Attempt %d should succeed".formatted(attempt));
                    return null;
                });
        }

        // 3. Verify: 6th attempt is rate limited
        final LoginCommand blockedCmd = new LoginCommand(Username.of("rate_test"), Password.of(VALID_PASSWORD.toCharArray()),
            IpAddress.of(testIP));
        final LoginResult blockedResult = loginHandler.handle(blockedCmd);

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
        final UserId userId = UserId.of(UUID.randomUUID());
        setupUserWithCredentials(userId, Username.of("multi_ip_test"));

        // 2. Execute: Attempts from different IPs should succeed independently
        final String[] ips = {"10.0.0.1", "10.0.0.2", "10.0.0.3"};
        for (final String ip : ips) {
            final LoginCommand cmd = new LoginCommand(Username.of("multi_ip_test"), Password.of(VALID_PASSWORD.toCharArray()),
                IpAddress.of(ip));
            final LoginResult result = loginHandler.handle(cmd);
            result.mapTo(success -> {
                    assertNotNull(success.tokenPair());
                    return null;
                })
                .orElse(ignored -> {
                    fail("Login from IP %s should succeed".formatted(ip));
                    return null;
                });
        }
    }

    @Test
    void testUserWithMultiplePermissions() {
        // 1. Setup: User with multiple permissions
        final UserId userId = UserId.of(UUID.randomUUID());
        setupUserWithCredentials(userId, Username.of("super_admin"));
        final User user = new User(userId, Username.of("super_admin"), Set.of(
            Permission.of("manage_users"),
            Permission.of("delete_accounts")
        ));
        userRepository.save(user);

        // 2. Verify: User has both permissions
        assertTrue(user.permissions().contains(Permission.of("manage_users")));
        assertTrue(user.permissions().contains(Permission.of("delete_accounts")));

        // 3. Execute: Login should work
        final LoginCommand cmd = new LoginCommand(Username.of("super_admin"), Password.of(VALID_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.100"));
        final LoginResult result = loginHandler.handle(cmd);

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
        final UserId userId = UserId.of(UUID.randomUUID());
        setupUserWithCredentials(userId, Username.of("immutable_test"));
        final User originalUser = new User(userId, Username.of("immutable_test"), Set.of());
        userRepository.save(originalUser);

        // 2. Execute: Login (which uses the user from repository)
        final LoginCommand cmd = new LoginCommand(Username.of("immutable_test"), Password.of(VALID_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.100"));
        final LoginResult result = loginHandler.handle(cmd);

        // 3. Verify: Original user object unchanged
        result.mapTo(ignored -> {
                assertTrue(originalUser.permissions().isEmpty());
                return null;
            })
            .orElse(ignored2 -> {
                fail("Login should have succeeded");
                return null;
            });
    }

    @Test
    void testPasswordSecurityWithCharArrays() {
        // 1. Setup: User
        final UserId userId = UserId.of(UUID.randomUUID());
        setupUserWithCredentials(userId, Username.of("security_test"));

        // 2. Execute: Login with char array password
        final char[] password = VALID_PASSWORD.toCharArray();
        final LoginCommand cmd = new LoginCommand(Username.of("security_test"), Password.of(password),
            IpAddress.of("192.168.1.100"));

        // Clear the original char array after command creation
        Arrays.fill(password, '\0');

        // 3. Verify: Command still has the correct password (cloned)
        final LoginResult result = loginHandler.handle(cmd);
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
        final Username username = Username.of("VALUE_OBJECT_TEST");
        final UserId userId = UserId.of(UUID.randomUUID());

        setupUserWithCredentials(userId, username);
        final User user = new User(userId, username, Set.of());
        userRepository.save(user);

        // 2. Execute: Login with normalized username
        final LoginCommand cmd = new LoginCommand(Username.of("value_object_test"), Password.of(VALID_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.100"));
        final LoginResult result = loginHandler.handle(cmd);

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
        final UserId userId = UserId.of(UUID.randomUUID());
        setupUserWithCredentials(userId, Username.of("clock_test"));

        final Instant t1 = clock.now();

        // 2. Execute: First login
        final LoginCommand cmd1 = new LoginCommand(Username.of("clock_test"), Password.of(VALID_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.100"));
        final LoginResult result1 = loginHandler.handle(cmd1);

        // 3. Advance clock
        clock.advanceSeconds(60);
        final Instant t2 = clock.now();

        // 4. Execute: Second login
        final LoginCommand cmd2 = new LoginCommand(Username.of("clock_test"), Password.of(VALID_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.101"));
        final LoginResult result2 = loginHandler.handle(cmd2);

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
        final UserId user1Id = UserId.of(UUID.randomUUID());
        final UserId user2Id = UserId.of(UUID.randomUUID());
        setupUserWithCredentials(user1Id, Username.of("persist1"));
        setupUserWithCredentials(user2Id, Username.of("persist2"));

        // 2. Verify: Repository persists users
        assertEquals(2, userRepository.size());
        assertTrue(userRepository.findCredentialsByUsername(Username.of("persist1")).isPresent());
        assertTrue(userRepository.findCredentialsByUsername(Username.of("persist2")).isPresent());
    }

    @Test
    void testEndToEndSecurityFlow() {
        // Complete realistic scenario:
        // 1. User registration
        final UserId userId = UserId.of(UUID.randomUUID());
        final Username username = Username.of("secure_user");
        setupUserWithCredentials(userId, username);

        // 2. Successful login
        final LoginCommand correctCmd = new LoginCommand(Username.of("secure_user"), Password.of(VALID_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.50"));
        final LoginResult correctResult = loginHandler.handle(correctCmd);
        correctResult.mapTo(success -> {
                assertNotNull(success.tokenPair());
                return null;
            })
            .orElse(ignored -> {
                fail("Correct login should have succeeded");
                return null;
            });

        // 3. Failed login attempt
        final LoginCommand wrongCmd = new LoginCommand(Username.of("secure_user"), Password.of(VALID_WRONG_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.50"));
        final LoginResult wrongResult = loginHandler.handle(wrongCmd);
        wrongResult.mapTo(ignored -> {
                fail("Wrong password login should have failed");
                return null;
            })
            .orElse(err -> {
                assertEquals("INVALID_CREDENTIALS", err.errorCode());
                return null;
            });

        // 4. Verify rate limit not hit (different IP, new user)
        final LoginCommand anotherUserCmd = new LoginCommand(Username.of("secure_user"), Password.of(VALID_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.51"));
        final LoginResult anotherResult = loginHandler.handle(anotherUserCmd);
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
        final UserId userId = UserId.of(UUID.randomUUID());
        final Username username = Username.of("powerful_user");
        setupUserWithCredentials(userId, username);

        final User user = new User(userId, username, Set.of(
            com.oodesigns.cas.domain.value.Permission.of("manage_users"),
            com.oodesigns.cas.domain.value.Permission.of("view_reports"),
            com.oodesigns.cas.domain.value.Permission.of("delete_accounts")
        ));

        userRepository.save(user);

        // 2. Execute: Login
        final LoginCommand loginCmd = new LoginCommand(Username.of("powerful_user"), Password.of(VALID_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.99"));

        final LoginResult result = loginHandler.handle(loginCmd);

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
        final UserId userId = UserId.of(UUID.randomUUID());
        final Username username = Username.of("basic_user");
        setupUserWithCredentials(userId, username);

        // 2. Execute: Login
        final LoginCommand loginCmd = new LoginCommand(Username.of("basic_user"), Password.of(VALID_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.88"));

        final LoginResult result = loginHandler.handle(loginCmd);

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

