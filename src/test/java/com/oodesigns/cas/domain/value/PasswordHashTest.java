package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PasswordHash value object.
 * Validates: bcrypt format, immutability, security (no plaintext exposure).
 */
class PasswordHashTest {

    // Real bcrypt hashes for testing
    private static final String BCRYPT_2A = "$2a$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";
    private static final String BCRYPT_2B = "$2b$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";
    private static final String BCRYPT_2Y = "$2y$12$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";

    @Test
    void testValidBcrypt2A() {
        PasswordHash hash = new PasswordHash(BCRYPT_2A);
        assertEquals(BCRYPT_2A, hash.asString());
    }

    @Test
    void testValidBcrypt2B() {
        PasswordHash hash = new PasswordHash(BCRYPT_2B);
        assertEquals(BCRYPT_2B, hash.asString());
    }

    @Test
    void testValidBcrypt2Y() {
        PasswordHash hash = new PasswordHash(BCRYPT_2Y);
        assertEquals(BCRYPT_2Y, hash.asString());
    }

    @Test
    void testInvalidPrefixThrows() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHash("$2$12$invalid"));
    }

    @Test
    void testPlaintextThrows() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHash("plaintext_password"));
    }

    @Test
    void testNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHash(null));
    }

    @Test
    void testEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHash(""));
    }

    @Test
    void testToStringMasksPassword() {
        PasswordHash hash = new PasswordHash(BCRYPT_2A);
        String str = hash.toString();
        assertFalse(str.contains(BCRYPT_2A));
        assertTrue(str.contains("***"));
    }

    @Test
    void testEqualityBasedOnHash() {
        PasswordHash hash1 = new PasswordHash(BCRYPT_2A);
        PasswordHash hash2 = new PasswordHash(BCRYPT_2A);
        assertEquals(hash1, hash2);
    }

    @Test
    void testInequalityDifferentHashes() {
        PasswordHash hash1 = new PasswordHash(BCRYPT_2A);
        PasswordHash hash2 = new PasswordHash(BCRYPT_2B);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void testHashCodeConsistency() {
        PasswordHash hash1 = new PasswordHash(BCRYPT_2A);
        PasswordHash hash2 = new PasswordHash(BCRYPT_2A);
        assertEquals(hash1.hashCode(), hash2.hashCode());
    }
}
