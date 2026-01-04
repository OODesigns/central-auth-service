package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Credentials;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.PasswordHash;
import com.oodesigns.cas.domain.value.UserCredential;
import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MockPasswordHasher Tests")
class MockPasswordHasherTest {

    private MockPasswordHasher mockHasher;
    private UserId testUserId;
    private String testPasswordHash;
    private String testRawPassword;

    @BeforeEach
    void setUp() {
        mockHasher = new MockPasswordHasher();
        testUserId = UserId.generate();
        // Valid bcrypt format for testing
        testPasswordHash = "$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";
        testRawPassword = "test_password_123";
    }

    @Test
    @DisplayName("Should register and retrieve password hash mapping")
    void shouldRegisterPasswordHash() {
        mockHasher.registerPasswordHash(testPasswordHash, testRawPassword);

        final PasswordHash hash = new PasswordHash(testPasswordHash);
        final UserCredential credential = new UserCredential(testUserId, hash);
        final Password password = new Password(testRawPassword.toCharArray());
        final Credentials credentials = new Credentials(credential, password);

        final Optional<UserId> result = mockHasher.verify(credentials);

        assertTrue(result.isPresent(), "Should return UserId when password matches");
        assertEquals(testUserId, result.get());
    }

    @Test
    @DisplayName("Should reject incorrect password")
    void shouldRejectIncorrectPassword() {
        mockHasher.registerPasswordHash(testPasswordHash, testRawPassword);

        final PasswordHash hash = new PasswordHash(testPasswordHash);
        final UserCredential credential = new UserCredential(testUserId, hash);
        final Password wrongPassword = new Password("wrong_password".toCharArray());
        final Credentials credentials = new Credentials(credential, wrongPassword);

        final Optional<UserId> result = mockHasher.verify(credentials);

        assertTrue(result.isEmpty(), "Should return empty when password doesn't match");
    }

    @Test
    @DisplayName("Should return empty when hash not registered")
    void shouldReturnEmptyWhenHashNotRegistered() {
        // Don't register any hash
        final PasswordHash hash = new PasswordHash(testPasswordHash);
        final UserCredential credential = new UserCredential(testUserId, hash);
        final Password password = new Password(testRawPassword.toCharArray());
        final Credentials credentials = new Credentials(credential, password);

        final Optional<UserId> result = mockHasher.verify(credentials);

        assertTrue(result.isEmpty(), "Should return empty when hash not registered");
    }

    @Test
    @DisplayName("Should reject null hash value in registration")
    void shouldRejectNullHashValue() {
        assertThrows(IllegalArgumentException.class,
            () -> mockHasher.registerPasswordHash(null, testRawPassword));
    }

    @Test
    @DisplayName("Should reject empty hash value in registration")
    void shouldRejectEmptyHashValue() {
        assertThrows(IllegalArgumentException.class,
            () -> mockHasher.registerPasswordHash("", testRawPassword));
    }

    @Test
    @DisplayName("Should reject null raw password in registration")
    void shouldRejectNullRawPassword() {
        assertThrows(IllegalArgumentException.class,
            () -> mockHasher.registerPasswordHash(testPasswordHash, null));
    }

    @Test
    @DisplayName("Should reject empty raw password in registration")
    void shouldRejectEmptyRawPassword() {
        assertThrows(IllegalArgumentException.class,
            () -> mockHasher.registerPasswordHash(testPasswordHash, ""));
    }

    @Test
    @DisplayName("Should reject null credentials in verify")
    void shouldRejectNullCredentials() {
        assertThrows(IllegalArgumentException.class,
            () -> mockHasher.verify(null));
    }

    @Test
    @DisplayName("Should register multiple password hashes")
    void shouldRegisterMultiplePasswords() {
        final String hash2 = "$2b$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";
        final String password2 = "another_password_456";
        final UserId userId2 = UserId.generate();

        mockHasher.registerPasswordHash(testPasswordHash, testRawPassword);
        mockHasher.registerPasswordHash(hash2, password2);

        // Verify first password
        final PasswordHash hash1 = new PasswordHash(testPasswordHash);
        final UserCredential cred1 = new UserCredential(testUserId, hash1);
        final Password pwd1 = new Password(testRawPassword.toCharArray());
        final Credentials credentials1 = new Credentials(cred1, pwd1);
        assertTrue(mockHasher.verify(credentials1).isPresent());

        // Verify second password
        final PasswordHash hashObj2 = new PasswordHash(hash2);
        final UserCredential cred2 = new UserCredential(userId2, hashObj2);
        final Password pwd2 = new Password(password2.toCharArray());
        final Credentials credentials2 = new Credentials(cred2, pwd2);
        assertTrue(mockHasher.verify(credentials2).isPresent());
    }

    @Test
    @DisplayName("Should implement PasswordVerifier interface")
    void shouldImplementPasswordVerifierInterface() {
        assertInstanceOf(Ports.PasswordVerifier.class, mockHasher);
    }
}

