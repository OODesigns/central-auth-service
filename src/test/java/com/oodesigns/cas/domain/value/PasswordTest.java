package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Password value object.
 * Validates: char[] handling, cloning, memory clearing, security.
 */
class PasswordTest {

    @Test
    void testValidPassword() {
        char[] chars = "myPassword123".toCharArray();
        Password password = new Password(chars);
        assertArrayEquals(chars, password.chars());
    }

    @Test
    void testPasswordCharArrayCloned() {
        char[] original = "password123".toCharArray();
        Password password = new Password(original);
        
        original[0] = 'X';
        
        assertEquals('p', password.chars()[0]);
    }

    @Test
    void testPasswordCharArrayNotMutableViaGetter() {
        Password password = new Password("password123".toCharArray());
        
        char[] retrieved = password.chars();
        retrieved[0] = 'X';
        
        assertEquals('p', password.chars()[0]);
    }

    @Test
    void testPasswordClear() {
        Password password = new Password("secret".toCharArray());
        
        password.clear();
        
        for (char c : password.chars()) {
            assertEquals('\0', c);
        }
    }

    @Test
    void testNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Password(null));
    }

    @Test
    void testEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Password(new char[0]));
    }

    @Test
    void testOfFactory() {
        char[] chars = "factoryPassword".toCharArray();
        Password password = Password.of(chars);
        assertArrayEquals(chars, password.chars());
    }

    @Test
    void testFromStringFactory() {
        Password password = Password.fromString("stringPassword");
        assertArrayEquals("stringPassword".toCharArray(), password.chars());
    }

    @Test
    void testFromStringNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> Password.fromString(null));
    }

    @Test
    void testFromStringEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> Password.fromString(""));
    }

    @Test
    void testToStringMasksPassword() {
        Password password = new Password("secret".toCharArray());
        String str = password.toString();
        assertFalse(str.contains("secret"));
        assertTrue(str.contains("***"));
    }

    @Test
    void testEquality() {
        Password pwd1 = new Password("pass123".toCharArray());
        Password pwd2 = new Password("pass123".toCharArray());
        assertEquals(pwd1, pwd2);
    }

    @Test
    void testInequality() {
        Password pwd1 = new Password("pass123".toCharArray());
        Password pwd2 = new Password("pass456".toCharArray());
        assertNotEquals(pwd1, pwd2);
    }

    @Test
    void testHashCodeConsistency() {
        Password pwd1 = new Password("pass123".toCharArray());
        Password pwd2 = new Password("pass123".toCharArray());
        assertEquals(pwd1.hashCode(), pwd2.hashCode());
    }
}
