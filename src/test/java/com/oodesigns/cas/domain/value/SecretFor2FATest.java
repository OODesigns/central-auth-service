package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TotpSecret Value Object Tests")
class SecretFor2FATest {

    private static final String VALID_SECRET = "JBSWY3DPEBLW64TMMQ======";
    private static final String VALID_SECRET_SHORT = "JBSWY3DPEBLW64TM";
    private static final String INVALID_SECRET_LOWERCASE = "jbswy3dpeblw64tmmq======";
    private static final String INVALID_SECRET_SYMBOLS = "JBSWY3DPEBLW64TM!@#$";
    private static final String INVALID_SECRET_SHORT = "JBSWY3DPEBLW64";

    @Test
    @DisplayName("Valid secret with padding creates TotpSecret")
    void testValidSecretWithPadding() {
        final SecretFor2FA secret = SecretFor2FA.of(VALID_SECRET);
        assertEquals(VALID_SECRET, secret.value());
        assertEquals(24, secret.length());
    }

    @Test
    @DisplayName("Valid secret minimum length (16 chars) creates TotpSecret")
    void testValidSecretMinimumLength() {
        final SecretFor2FA secret = SecretFor2FA.of(VALID_SECRET_SHORT);
        assertEquals(VALID_SECRET_SHORT, secret.value());
        assertEquals(16, secret.length());
    }

    @Test
    @DisplayName("Null secret throws NullPointerException")
    void testNullSecretThrows() {
        assertThrows(NullPointerException.class, () -> SecretFor2FA.of(null));
    }

    @Test
    @DisplayName("Lowercase characters throw IllegalArgumentException")
    void testLowercaseThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecretFor2FA.of(INVALID_SECRET_LOWERCASE));
    }

    @Test
    @DisplayName("Invalid characters throw IllegalArgumentException")
    void testInvalidCharactersThrow() {
        assertThrows(IllegalArgumentException.class, () -> SecretFor2FA.of(INVALID_SECRET_SYMBOLS));
    }

    @Test
    @DisplayName("Secret below minimum length throws IllegalArgumentException")
    void testSecretTooShortThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecretFor2FA.of(INVALID_SECRET_SHORT));
    }

    @Test
    @DisplayName("Empty string throws IllegalArgumentException")
    void testEmptySecretThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecretFor2FA.of(""));
    }

    @Test
    @DisplayName("Valid Base32 without padding creates TotpSecret")
    void testValidSecretWithoutPadding() {
        final String secretNoPadding = "JBSWY3DPEBLW64TMMQ";
        final SecretFor2FA secret = SecretFor2FA.of(secretNoPadding);
        assertEquals(secretNoPadding, secret.value());
    }

    @Test
    @DisplayName("Two secrets with same value are equal")
    void testEqualSecrets() {
        final SecretFor2FA secret1 = SecretFor2FA.of(VALID_SECRET);
        final SecretFor2FA secret2 = SecretFor2FA.of(VALID_SECRET);
        assertEquals(secret1, secret2);
    }

    @Test
    @DisplayName("Two secrets with different values are not equal")
    void testNotEqualSecrets() {
        final SecretFor2FA secret1 = SecretFor2FA.of(VALID_SECRET);
        final SecretFor2FA secret2 = SecretFor2FA.of(VALID_SECRET_SHORT);
        assertNotEquals(secret1, secret2);
    }

    @Test
    @DisplayName("Secret value is returned correctly")
    void testSecretValue() {
        final SecretFor2FA secret = SecretFor2FA.of(VALID_SECRET);
        final String value = secret.value();
        assertEquals(VALID_SECRET, value);
        // Verify returned value is exactly the secret string
        assertEquals(value, secret.getSecret());
    }
}

