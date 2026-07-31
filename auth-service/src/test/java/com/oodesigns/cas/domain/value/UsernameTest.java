package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Username value object.
 * Validates: format, normalization, immutability.
 */
class UsernameTest {

    @Test
    void testValidUsername() {
        final Username username = Username.of("john_doe");
        assertEquals("john_doe", username.value());
    }

    @Test
    void testNormalizesToLowercase() {
        final Username username = Username.of("JohnDoe");
        assertEquals("johndoe", username.value());
    }

    @Test
    void testValidWithNumbers() {
        final Username username = Username.of("user123");
        assertEquals("user123", username.value());
    }

    @Test
    void testValidWithHyphen() {
        final Username username = Username.of("user-name");
        assertEquals("user-name", username.value());
    }

    @Test
    void testValidWithUnderscore() {
        final Username username = Username.of("user_name");
        assertEquals("user_name", username.value());
    }

    @Test
    void testValidMinimumLength() {
        final Username username = Username.of("abc");
        assertEquals("abc", username.value());
    }

    @Test
    void testTooShortThrows() {
        assertThrows(IllegalArgumentException.class, () -> Username.of("ab"));
    }

    @Test
    void testTooLongThrows() {
        final String longName = "a".repeat(51);
        assertThrows(IllegalArgumentException.class, () -> Username.of(longName));
    }

    @Test
    void testInvalidCharactersThrows() {
        assertThrows(IllegalArgumentException.class, () -> Username.of("user@domain"));
        assertThrows(IllegalArgumentException.class, () -> Username.of("user name"));
        assertThrows(IllegalArgumentException.class, () -> Username.of("user.com"));
    }

    @Test
    void testEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> Username.of(""));
    }

    @Test
    void testNullThrows() {
        assertThrows(NullPointerException.class, () -> Username.of(null));
    }

    @Test
    void testEqualityBasedOnValue() {
        final Username user1 = Username.of("john_doe");
        final Username user2 = Username.of("JOHN_DOE");
        assertEquals(user1, user2);
    }

    @Test
    void testInequalityDifferentValues() {
        final Username user1 = Username.of("john");
        final Username user2 = Username.of("jane");
        assertNotEquals(user1, user2);
    }

    @Test
    void testHashCodeConsistency() {
        final Username user1 = Username.of("john_doe");
        final Username user2 = Username.of("JOHN_DOE");
        assertEquals(user1.hashCode(), user2.hashCode());
    }
}
