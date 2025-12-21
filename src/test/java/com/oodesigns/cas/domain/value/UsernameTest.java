package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Username value object.
 * Validates: format, normalization, immutability.
 */
public class UsernameTest {

    @Test
    public void testValidUsername() {
        Username username = new Username("john_doe");
        assertEquals("john_doe", username.asString());
    }

    @Test
    public void testNormalizesToLowercase() {
        Username username = new Username("JohnDoe");
        assertEquals("johndoe", username.asString());
    }

    @Test
    public void testValidWithNumbers() {
        Username username = new Username("user123");
        assertEquals("user123", username.asString());
    }

    @Test
    public void testValidWithHyphen() {
        Username username = new Username("user-name");
        assertEquals("user-name", username.asString());
    }

    @Test
    public void testValidWithUnderscore() {
        Username username = new Username("user_name");
        assertEquals("user_name", username.asString());
    }

    @Test
    public void testValidMinimumLength() {
        Username username = new Username("abc");
        assertEquals("abc", username.asString());
    }

    @Test
    public void testTooShortThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Username("ab"));
    }

    @Test
    public void testTooLongThrows() {
        String longName = "a".repeat(51);
        assertThrows(IllegalArgumentException.class, () -> new Username(longName));
    }

    @Test
    public void testInvalidCharactersThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Username("user@domain"));
        assertThrows(IllegalArgumentException.class, () -> new Username("user name"));
        assertThrows(IllegalArgumentException.class, () -> new Username("user.com"));
    }

    @Test
    public void testEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Username(""));
    }

    @Test
    public void testNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Username(null));
    }

    @Test
    public void testEqualityBasedOnValue() {
        Username user1 = new Username("john_doe");
        Username user2 = new Username("JOHN_DOE");
        assertEquals(user1, user2);
    }

    @Test
    public void testInequalityDifferentValues() {
        Username user1 = new Username("john");
        Username user2 = new Username("jane");
        assertNotEquals(user1, user2);
    }

    @Test
    public void testHashCodeConsistency() {
        Username user1 = new Username("john_doe");
        Username user2 = new Username("JOHN_DOE");
        assertEquals(user1.hashCode(), user2.hashCode());
    }
}
