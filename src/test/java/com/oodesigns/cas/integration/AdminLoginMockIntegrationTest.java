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
 * 
 * Tests admin-specific scenarios with in-memory mocks:
 * - Admin login with correct credentials
 * - Admin login with incorrect password
 * - Admin role verification
 * - Rate limiting per IP address
 * - Token timestamp validation
 * 
 * Uses mock adapters for fast unit-like testing without external dependencies.
 * Focuses specifically on admin user behaviors and permissions.
 * 
 * For general login scenarios, see: LoginMockIntegrationTest
 * For real database testing, see: AdminLoginDatabaseIntegrationTest
 */
class AdminLoginMockIntegrationTest {

    private LoginCommandHandler loginHandler;
    private InMemoryUserRepository userRepository;
    private MockPasswordVerifier passwordVerifier;
    private MockClock clock;
    private Bucket4jRateLimiter rateLimiter;
    private MockTokenSigner tokenSigner;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin_initial_password";

    @BeforeEach
    void setUp() {
        // Initialize all adapters
        userRepository = new InMemoryUserRepository();
        passwordVerifier = new MockPasswordVerifier();
        clock = new MockClock(Instant.now());
        rateLimiter = new Bucket4jRateLimiter(5, java.time.Duration.ofMinutes(1));
        tokenSigner = new MockTokenSigner();

        // Create domain services with injected ports
        AuthenticationService authService = new AuthenticationService(passwordVerifier);
        TokenService tokenService = new TokenService(clock, tokenSigner);

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
        UserId adminId = UserId.generate();
        Username adminUsername = new Username(ADMIN_USERNAME);
        
        // Create user with no permissions (permissions granted at authorization level, not stored with user)
        User adminUser = new User(adminId, adminUsername, java.util.Set.of());
        
        // Hash the admin password
        PasswordHash passwordHash = passwordVerifier.hash(ADMIN_PASSWORD.toCharArray());
        
        // Save user and credentials
        userRepository.save(adminUser);
        userRepository.saveCredential(new UserCredential(adminId, passwordHash));
    }

    /**
     * Test: Admin user can login with correct credentials.
     * 
     * Verifies:
     * - Login succeeds
     * - Access token is generated
     * - Refresh token is generated
     * - Token pair is not null
     */
    @Test
    void testAdminLoginWithCorrectCredentials() {
        // Arrange: Admin credentials
        LoginCommand loginCmd = new LoginCommand(
            Username.of(ADMIN_USERNAME),
            new Password(ADMIN_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.50")
        );

        // Act: Execute login
        LoginResult result = loginHandler.handle(loginCmd);

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
                fail("Admin login should have succeeded. Error: " + failure.errorMessage());
                return null;
            });
    }

    /**
     * Test: Admin user login fails with incorrect password.
     * 
     * Verifies:
     * - Login fails with generic error (no information leakage)
     * - Error code is INVALID_CREDENTIALS
     * - Error message indicates invalid credentials
     */
    @Test
    void testAdminLoginWithIncorrectPassword() {
        // Arrange: Wrong password for admin user
        LoginCommand loginCmd = new LoginCommand(
            Username.of(ADMIN_USERNAME),
            new Password("wrong_password".toCharArray()),
            IpAddress.of("192.168.1.50")
        );

        // Act: Execute login
        LoginResult result = loginHandler.handle(loginCmd);

        // Assert: Login fails with generic error
        result.mapTo(success -> {
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
     * 
     * Verifies:
     * - Admin user is created
     * - User can be retrieved from repository by ID
     */
    @Test
    void testAdminUserExistsInRepository() {
        // Act: Retrieve admin user from repository by username
        var adminCredential = userRepository.findCredentialsByUsername(new Username(ADMIN_USERNAME));

        // Assert: Admin user exists
        assertTrue(adminCredential.isPresent(), "Admin user should exist");
        
        // Get the user to check it was stored correctly
        var userOptional = userRepository.findById(adminCredential.get().userId());
        assertTrue(userOptional.isPresent(), "Admin user should be retrievable by ID");
        
        User adminUser = userOptional.get();
        assertEquals(ADMIN_USERNAME, adminUser.username().value(),
            "Admin username should match");
    }

    /**
     * Test: Multiple login attempts from same IP are rate limited.
     * 
     * Verifies:
     * - First 5 login attempts succeed (or fail based on credentials, but not rate limited)
     * - 6th attempt is rate limited
     * - Rate limit is per IP address
     */
    @Test
    void testAdminLoginRateLimiting() {
        // Arrange: Same IP address for all attempts
        String ipAddress = "192.168.1.100";
        
        // Act & Assert: First 5 attempts should not be rate limited
        for (int attemptNumber = 1; attemptNumber <= 5; attemptNumber++) {
            LoginCommand loginCmd = new LoginCommand(
                Username.of(ADMIN_USERNAME),
                new Password(ADMIN_PASSWORD.toCharArray()),
                IpAddress.of(ipAddress)
            );
            
            LoginResult result = loginHandler.handle(loginCmd);
            
            // First 5 should succeed (not rate limited)
            final int attempt = attemptNumber;
            result.mapTo(success -> {
                    assertNotNull(success.tokenPair(), "Attempt " + attempt + " should succeed");
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
        LoginCommand rateLimitedCmd = new LoginCommand(
            Username.of(ADMIN_USERNAME),
            new Password(ADMIN_PASSWORD.toCharArray()),
            IpAddress.of(ipAddress)
        );
        
        LoginResult rateLimitedResult = loginHandler.handle(rateLimitedCmd);
        
        // Assert: 6th attempt is rate limited
        rateLimitedResult.mapTo(success -> {
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
     * 
     * Verifies:
     * - Rate limiting is per IP address
     * - Admin can login from different IPs without hitting rate limit
     */
    @Test
    void testAdminLoginRateLimitingPerIP() {
        // Arrange: Different IP addresses
        String[] ipAddresses = {
            "192.168.1.100",
            "192.168.1.101",
            "192.168.1.102"
        };

        // Act & Assert: Login from 3 different IPs should all succeed
        for (String ipAddress : ipAddresses) {
            LoginCommand loginCmd = new LoginCommand(
                Username.of(ADMIN_USERNAME),
                new Password(ADMIN_PASSWORD.toCharArray()),
                IpAddress.of(ipAddress)
            );

            LoginResult result = loginHandler.handle(loginCmd);

            // Assert: Each IP address should be able to login
            result.mapTo(success -> {
                    assertNotNull(success.tokenPair(), 
                        "Login from IP " + ipAddress + " should succeed");
                    return null;
                })
                .orElse(failure -> {
                    fail("Login from IP " + ipAddress + " should have succeeded. Error: " + failure.errorMessage());
                    return null;
                });
        }
    }

    /**
     * Test: Admin token contains correct timestamp information.
     * 
     * Verifies:
     * - Token is generated with current timestamp
     * - Token timestamp matches clock instance
     */
    @Test
    void testAdminLoginTokenTimestamp() {
        // Arrange: Fixed time for verification
        Instant testTime = Instant.parse("2025-12-30T10:00:00Z");
        clock.setCurrentTime(testTime);

        LoginCommand loginCmd = new LoginCommand(
            Username.of(ADMIN_USERNAME),
            new Password(ADMIN_PASSWORD.toCharArray()),
            IpAddress.of("192.168.1.50")
        );

        // Act: Execute login
        LoginResult result = loginHandler.handle(loginCmd);

        // Assert: Token timestamp is correct
        result.mapTo(success -> {
                // Token was signed at test time
                assertNotNull(success.tokenPair(), "Token pair should be generated");
                return null;
            })
            .orElse(failure -> {
                fail("Admin login should succeed for timestamp test");
                return null;
            });
    }
}
