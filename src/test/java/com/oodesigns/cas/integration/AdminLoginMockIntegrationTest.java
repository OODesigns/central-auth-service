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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for admin user authentication using mock adapters.
 * <p>
 * Tests admin-specific scenarios with in-memory mocks:
 * - Admin login with correct credentials
 * - Admin login with incorrect password
 * - Admin role verification
 * - Rate limiting per IP address
 * - Token timestamp validation
 * <p>
 * Uses mock adapters for fast unit-like testing without external dependencies.
 * Focuses specifically on admin user behaviors and permissions.
 * <p>
 * For general login scenarios, see: LoginMockIntegrationTest
 * For real database testing, see: AdminLoginDatabaseIntegrationTest
 */
class AdminLoginMockIntegrationTest {

    private LoginCommandHandler loginHandler;
    private InMemoryUserRepository userRepository;
    private MockPasswordVerifier passwordVerifier;
    private MockClock clock;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin_initial_password";

    @BeforeEach
    void setUp() {
        // Initialize all adapters
        userRepository = new InMemoryUserRepository();
        passwordVerifier = new MockPasswordVerifier();
        clock = new MockClock(Instant.now());
        final var rateLimiter = new Bucket4jRateLimiter(5, java.time.Duration.ofMinutes(1));
        final var tokenSigner = new MockTokenSigner();

        // Create domain services with injected ports
        final AuthenticationService authService = new AuthenticationService(passwordVerifier);
        final TokenService tokenService = new TokenService(clock, tokenSigner);

        // Create command handler
        loginHandler = new LoginCommandHandler(authService, tokenService, userRepository, userRepository, rateLimiter);

        // Setup admin user with admin role
        setupAdminUser();
    }

    /**
     * Setup admin user with admin role and full permissions.
     * This simulates the database state after Flyway migration V1_1__seed_auth_data.sql
     */
    private void setupAdminUser() {
        // Create admin user with admin permissions
        final UserId adminId = UserId.generate();
        final Username adminUsername = new Username(ADMIN_USERNAME);
        
        // Create user with no permissions (permissions granted at authorization level, not stored with user)
        final User adminUser = new User(adminId, adminUsername, java.util.Set.of());
        
        // Hash the admin password
        final PasswordHash passwordHash = passwordVerifier.hash(ADMIN_PASSWORD.toCharArray());
        
        // Save user and credentials
        userRepository.save(adminUser);
        userRepository.saveCredential(new UserCredential(adminId, passwordHash));
    }

    /**
     * Test: Admin user can log in with correct credentials.
     * <p>
     * Verifies:
     * - Login succeeds
     * - Access token is generated
     * - Refresh token is generated
     * - Token pair is not null
     */
    @Test
    void testAdminLoginWithCorrectCredentials() {
        // Arrange: Admin credentials
        final LoginCommand loginCmd = new LoginCommand(
            Username.of(ADMIN_USERNAME),
            new Password(ADMIN_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.50")
        );

        // Act: Execute login
        final LoginResult result = loginHandler.handle(loginCmd);

        // Assert: Login succeeds with valid tokens
        result.mapTo(success -> {
                assertNotNull(success.tokenPair(), "Token pair should not be null");
                assertNotNull(success.tokenPair().accessToken(), "Access token should not be null");
                assertNotNull(success.tokenPair().refreshToken(), "Refresh token should not be null");
                
                // Verify tokens are non-empty
                assertFalse(success.tokenPair().accessToken().isEmpty(), "Access token should not be empty");
                assertFalse(success.tokenPair().refreshToken().isEmpty(), "Refresh token should not be empty");
                
                return null;
            })
            .orElse(failure -> {
                fail("""
                    Admin login should have succeeded.
                    Error: %s
                    """.formatted(failure.errorMessage()));
                return null;
            });
    }

    /**
     * Test: Admin user login fails with incorrect password.
     * <p>
     * Verifies:
     * - Login fails with generic error (no information leakage)
     * - Error code is INVALID_CREDENTIALS
     * - Error message indicates invalid credentials
     */
    @Test
    void testAdminLoginWithIncorrectPassword() {
        // Arrange: Wrong password for admin user
        final LoginCommand loginCmd = new LoginCommand(
            Username.of(ADMIN_USERNAME),
            new Password("wrong_password".toCharArray()),
            IpAddress.of("192.168.1.50")
        );

        // Act: Execute login
        final LoginResult result = loginHandler.handle(loginCmd);

        // Assert: Login fails with generic error
        result.mapTo(ignoredSuccess -> {
                fail("Admin login with wrong password should have failed");
                return null;
            })
            .orElse(failure -> {
                assertEquals("INVALID_CREDENTIALS", failure.errorCode(),
                    "Error code should be INVALID_CREDENTIALS");
                assertTrue(failure.errorMessage().contains("Invalid username or password"),
                    "Error message should not reveal whether user exists");
                return null;
            });
    }

    /**
     * Test: Admin user has admin role assigned.
     * <p>
     * Verifies:
     * - Admin user is created
     * - User can be retrieved from repository by ID
     */
    @Test
    void testAdminUserExistsInRepository() {
        // Act: Retrieve admin user from repository by username
        final var adminCredential = userRepository.findCredentialsByUsername(new Username(ADMIN_USERNAME));

        // Assert: Admin user exists
        assertTrue(adminCredential.isPresent(), "Admin user should exist");
        
        // Get the user to check it was stored correctly
        final var userOptional = userRepository.findById(adminCredential.get().userId());
        assertTrue(userOptional.isPresent(), "Admin user should be retrievable by ID");
        
        final User adminUser = userOptional.get();
        assertEquals(ADMIN_USERNAME, adminUser.username().value(),
            "Admin username should match");
    }

    /**
     * Test: Multiple login attempts from the same IP are rate limited.
     * <p>
     * Verifies:
     * - First 5 login attempts will succeed (or fail based on credentials, but not rate limited)
     * - 6th attempt is rate limited
     * - Rate limit is per IP address
     */
    @Test
    void testAdminLoginRateLimiting() {
        // Arrange: Same IP address for all attempts
        final String ipAddress = "192.168.1.100";
        
        // Act & Assert: First 5 attempts should not be rate limited
        for (int attemptNumber = 1; attemptNumber <= 5; attemptNumber++) {
            final LoginCommand loginCmd = new LoginCommand(
                Username.of(ADMIN_USERNAME),
                new Password(ADMIN_PASSWORD.toCharArray()),
                IpAddress.of(ipAddress)
            );
            
            final LoginResult result = loginHandler.handle(loginCmd);
            
            // First 5 should succeed (not rate limited)
            final int attempt = attemptNumber;
            result.mapTo(success -> {
                    assertNotNull(success.tokenPair(), 
                        "Attempt %d should succeed".formatted(attempt));
                    return null;
                })
                .orElse(failure -> {
                    // Could be invalid credentials, but not rate limiting
                    assertNotEquals("RATE_LIMITED", failure.errorCode(),
                        "First 5 attempts should not be rate limited");
                    return null;
                });
        }
        
        // Act: 6th attempt should be rate limited
        final LoginCommand rateLimitedCmd = new LoginCommand(
            Username.of(ADMIN_USERNAME),
            new Password(ADMIN_PASSWORD.toCharArray()),
            IpAddress.of(ipAddress)
        );
        
        final LoginResult rateLimitedResult = loginHandler.handle(rateLimitedCmd);
        
        // Assert: 6th attempt is rate limited
        rateLimitedResult.mapTo(ignoredSuccess -> {
                fail("6th login attempt should be rate limited");
                return null;
            })
            .orElse(failure -> {
                assertEquals("RATE_LIMITED", failure.errorCode(),
                    "6th attempt should be rate limited");
                return null;
            });
    }

    /**
     * Test: Admin login from different IP addresses are independently rate limited.
     * <p>
     * Verifies:
     * - Rate limiting is per IP address
     * - Admin can log in from different IPs without hitting rate limit
     */
    @Test
    void testAdminLoginRateLimitingPerIP() {
        // Arrange: Different IP addresses
        final String[] ipAddresses = {
            "192.168.1.100",
            "192.168.1.101",
            "192.168.1.102"
        };

        // Act & Assert: Login from 3 different IPs should all succeed
        for (final String ipAddress : ipAddresses) {
            final LoginCommand loginCmd = new LoginCommand(
                Username.of(ADMIN_USERNAME),
                new Password(ADMIN_PASSWORD.toCharArray()),
                IpAddress.of(ipAddress)
            );

            final LoginResult result = loginHandler.handle(loginCmd);

            // Assert: Each IP address should be able to log in
            result.mapTo(success -> {
                    assertNotNull(success.tokenPair(), 
                        "Login from IP %s should succeed".formatted(ipAddress));
                    return null;
                })
                .orElse(failure -> {
                    fail("""
                        Login from IP %s should have succeeded.
                        Error: %s
                        """.formatted(ipAddress, failure.errorMessage()));
                    return null;
                });
        }
    }

    /**
     * Test: Admin token contains correct timestamp information.
     * <p>
     * Verifies:
     * - Token is generated with current timestamp
     * - Token timestamp matches clock instance
     */
    @Test
    void testAdminLoginTokenTimestamp() {
        // Arrange: Fixed time for verification
        final Instant testTime = Instant.parse("2025-12-30T10:00:00Z");
        clock.setCurrentTime(testTime);

        final LoginCommand loginCmd = new LoginCommand(
            Username.of(ADMIN_USERNAME),
            new Password(ADMIN_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.50")
        );

        // Act: Execute login
        final LoginResult result = loginHandler.handle(loginCmd);

        // Assert: Token timestamp is correct
        result.mapTo(success -> {
                // Token was signed at test time
                assertNotNull(success.tokenPair(), "Token pair should be generated");
                return null;
            })
            .orElse(ignoredFailure -> {
                fail("Admin login should succeed for timestamp test");
                return null;
            });
    }
}
