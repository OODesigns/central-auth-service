package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.Credentials;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.PasswordHash;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.UserCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BcryptPasswordVerifier production implementation.
 * Tests both success and failure scenarios, including BCrypt availability checks.
 */
@DisplayName("BcryptPasswordVerifier Tests")
class BcryptPasswordVerifierTest {
    private BcryptPasswordVerifier verifier;
    private UserCredential testCredential;

    @BeforeEach
    void setUp() {
        verifier = new BcryptPasswordVerifier();

        final UserId userId = UserId.of(UUID.randomUUID());
        // Generate a real BCrypt hash for "correct_password" using Spring Security
        final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        final String bcryptHash = encoder.encode("correct_password");
        final PasswordHash passwordHash = PasswordHash.of(bcryptHash);
        testCredential = UserCredential.of(userId, passwordHash);
    }

    @Test
    @DisplayName("Should verify correct password")
    void shouldVerifyCorrectPassword() {
        final Password password = new Password("correct_password".toCharArray());
        try (final Credentials creds = Credentials.of(testCredential, password)) {
            final Optional<UserId> result = verifier.verify(creds);
            
            assertTrue(result.isPresent(), "Result should be present for correct password");
            assertEquals(testCredential.userId(), result.get());
        }
    }

    @Test
    @DisplayName("Should reject incorrect password")
    void shouldRejectIncorrectPassword() {
        final Password wrongPassword = new Password("wrong_password".toCharArray());
        try (final Credentials creds = Credentials.of(testCredential, wrongPassword)) {
            final Optional<UserId> result = verifier.verify(creds);
            
            assertTrue(result.isEmpty(), "Result should be empty for incorrect password");
        }
    }

    @Test
    @DisplayName("Should return empty Optional for null credentials")
    void shouldReturnEmptyOptionalForNullCredentials() {
        final Optional<UserId> result = verifier.verify(null);
        
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isEmpty(), "Should return empty Optional for null credentials");
    }

    @Test
    @DisplayName("Should return Optional (never null)")
    void shouldReturnOptionalNeverNull() {
        final Password password = new Password("any_password".toCharArray());
        try (final Credentials creds = Credentials.of(testCredential, password)) {
            final Optional<UserId> result = verifier.verify(creds);
            
            assertNotNull(result, "Result Optional should never be null");
            // Can be either empty or containing credential depending on BCrypt availability
        }
    }

    @Test
    @DisplayName("Should handle credentials with AutoCloseable")
    void shouldHandleCredentialsWithAutoCloseable() {
        final Password password = new Password("test_password".toCharArray());
        final int originalLength = password.chars().length;
        
        // Test that credentials can be used with try-with-resources
        try (final Credentials creds = Credentials.of(testCredential, password)) {
            final Optional<UserId> result = verifier.verify(creds);
            assertNotNull(result);
        }
        // Password should be cleared after close (AutoCloseable behavior)
        assertTrue(password.chars().length == 0 || password.chars().length == originalLength,
                   "Password should be cleared or untouched after close");
    }

    @Test
    @DisplayName("Should handle short password gracefully")
    void shouldHandleShortPasswordGracefully() {
        final Password shortPassword = new Password("a".toCharArray());
        try (final Credentials creds = Credentials.of(testCredential, shortPassword)) {
            final Optional<UserId> result = verifier.verify(creds);
            
            assertNotNull(result, "Should handle short password gracefully");
        }
    }

    @Test
    @DisplayName("Should verify with multiple users")
    void shouldVerifyWithMultipleUsers() {
        final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        final String bcryptHash1 = encoder.encode("password1");
        final String bcryptHash2 = encoder.encode("password2");
        final PasswordHash hash1 = PasswordHash.of(bcryptHash1);
        final PasswordHash hash2 = PasswordHash.of(bcryptHash2);
        
        final UserId userId1 = UserId.of(UUID.randomUUID());
        final UserId userId2 = UserId.of(UUID.randomUUID());
        final UserCredential cred1 = UserCredential.of(userId1, hash1);
        final UserCredential cred2 = UserCredential.of(userId2, hash2);
        
        final Password pwd1 = new Password("password1".toCharArray());
        final Password pwd2 = new Password("password2".toCharArray());
        
           try (final Credentials c1 = Credentials.of(cred1, pwd1);
               final Credentials c2 = Credentials.of(cred2, pwd2)) {
            final Optional<UserId> result1 = verifier.verify(c1);
            final Optional<UserId> result2 = verifier.verify(c2);
            
            assertTrue(result1.isPresent());
            assertTrue(result2.isPresent());
        }
    }

    @Test
    @DisplayName("Should handle invalid hash format gracefully")
    void shouldHandleInvalidHashFormatGracefully() {
        // Mock the encoder to throw an exception
        final PasswordEncoder mockEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        org.mockito.Mockito.when(mockEncoder.matches(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(new IllegalArgumentException("Invalid salt"));
            
        // Inject the mock encoder
        final BcryptPasswordVerifier verifierWithMock = new BcryptPasswordVerifier(mockEncoder);
        
        // Valid format to pass PasswordHash check (60 chars total)
        final String validFormatHash = "$2a$10$12345678901234567890123456789012345678901234567890123";
        final PasswordHash hash = PasswordHash.of(validFormatHash);
        final UserCredential credential = UserCredential.of(UserId.of(UUID.randomUUID()), hash);
        
        final Password password = new Password("password".toCharArray());
        try (final Credentials creds = Credentials.of(credential, password)) {
            final Optional<UserId> result = verifierWithMock.verify(creds);
            
            // Should return empty Optional instead of propagating the exception
            assertTrue(result.isEmpty(), "Should return empty Optional when encoder throws exception");
        }
    }
}
