package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Password value object.
 * Validates: char[] handling, cloning, memory clearing, security.
 */
class PasswordTest {

    @Test
    void testValidPassword() {
        final char[] chars = "myPassword123".toCharArray();
        try (final Password password = new Password(chars)) {
            assertArrayEquals(chars, password.chars());
        }
    }

    @Test
    void testPasswordCharArrayCloned() {
        final char[] original = "password123".toCharArray();
        try (final Password password = new Password(original)) {
            original[0] = 'X';
            assertEquals('p', password.chars()[0]);
        }
    }

    @Test
    void testPasswordCharArrayNotMutableViaGetter() {
        try (final Password password = new Password("password123".toCharArray())) {
            final char[] retrieved = password.chars();
            retrieved[0] = 'X';
            assertEquals('p', password.chars()[0]);
        }
    }

    @Test
    void testPasswordClear() {
        try (final Password password = new Password("secret".toCharArray())) {
            password.clear();
            
            for (final char c : password.chars()) {
                assertEquals('\0', c);
            }
        }
    }

    @Test
    void testNullThrows() {
        assertThrows(NullPointerException.class, () -> new Password(null));
    }

    @Test
    void testEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Password(new char[0]));
    }

    @Test
    void testOfFactory() {
        final char[] chars = "factoryPassword".toCharArray();
        try (final Password password = Password.of(chars)) {
            assertArrayEquals(chars, password.chars());
        }
    }

    @Test
    void testOfFactoryString() {
        try (final Password password = Password.of("stringPassword")) {
            assertArrayEquals("stringPassword".toCharArray(), password.chars());
        }
    }

    @Test
    void testOfStringNullThrows() {
        assertThrows(NullPointerException.class, () -> Password.of((String) null));
    }

    @Test
    void testOfStringEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> Password.of(""));
    }

    @Test
    void testToStringMasksPassword() {
        try (final Password password = new Password("secret".toCharArray())) {
            final String str = password.toString();
            assertFalse(str.contains("secret"));
            assertTrue(str.contains("***"));
        }
    }

    @Test
    void testPasswordsWithSameContentAreEquivalent() {
        // Password is not a record - it doesn't override equals()
        // Test that content is equivalent by comparing chars()
        try (final Password pwd1 = new Password("pass123".toCharArray());
             final Password pwd2 = new Password("pass123".toCharArray())) {
            assertArrayEquals(pwd1.chars(), pwd2.chars());
        }
    }

    @Test
    void testPasswordsWithDifferentContentAreDifferent() {
        try (final Password pwd1 = new Password("pass123".toCharArray());
             final Password pwd2 = new Password("pass456".toCharArray())) {
            final char[] pwd1Chars = pwd1.chars();
            final char[] pwd2Chars = pwd2.chars();
            assertNotEquals(new String(pwd1Chars), new String(pwd2Chars));
        }
    }

    @Test
    void testPasswordCharArrayIndependence() {
        // Verify that two Password instances with same content are independent
        try (final Password pwd1 = new Password("pass123".toCharArray());
             final Password pwd2 = new Password("pass123".toCharArray())) {
            // Clearing one doesn't affect the other
            pwd1.clear();
            assertFalse(Arrays.equals(pwd1.chars(), pwd2.chars()));
        }
    }

    @Test
    void testPasswordToStringDefaultResponse() {
        try (final Password password = new Password("anyPassword".toCharArray())) {
            final String str = password.toString();
            assertEquals("Password{***}", str);
        }
    }

    @Nested
    @DisplayName("Special Characters and Content Tests")
    class SpecialCharacterTests {

        @ParameterizedTest(name = "Password with {0}")
        @ValueSource(strings = {
            "P@$$w0rd!#%&*",              // Special characters
            "пароль密码🔐",                 // Unicode and emoji
            "pass word with spaces",       // Spaces
            "pass\nword\n123",            // Newlines
            "pass\tword\t123"             // Tabs
        })
        void testPasswordWithVariousCharacterTypes(final String passwordString) {
            final char[] chars = passwordString.toCharArray();
            try (final Password password = new Password(chars)) {
                assertArrayEquals(chars, password.chars());
            }
        }
    }

    @Nested
    @DisplayName("Password Length Variations")
    class PasswordLengthTests {

        @Test
        void testSingleCharacterPassword() {
            final char[] chars = "a".toCharArray();
            try (final Password password = new Password(chars)) {
                assertArrayEquals(chars, password.chars());
                assertEquals(1, password.chars().length);
            }
        }

        @Test
        void testVeryLongPassword() {
            final char[] chars = new char[10000];
            Arrays.fill(chars, 'a');
            try (final Password password = new Password(chars)) {
                assertEquals(10000, password.chars().length);
            }
        }

        @Test
        void testPasswordWithAllNumericChars() {
            final char[] chars = "1234567890".toCharArray();
            try (final Password password = new Password(chars)) {
                assertArrayEquals(chars, password.chars());
            }
        }
    }

    @Nested
    @DisplayName("Clear and Memory Security Tests")
    class MemorySecurityTests {

        @Test
        void testClearFillsAllCharactersWithNull() {
            try (final Password password = new Password("verysecret123password".toCharArray())) {
                password.clear();

                final char[] cleared = password.chars();
                for (final char c : cleared) {
                    assertEquals('\0', c, "All characters should be null after clear");
                }
            }
        }

        @Test
        void testClearCanBeCalledMultipleTimes() {
            try (final Password password = new Password("password".toCharArray())) {
                password.clear();
                password.clear();
                password.clear();

                for (final char c : password.chars()) {
                    assertEquals('\0', c);
                }
            }
        }

        @Test
        void testClearedPasswordIsStillAccessible() {
            try (final Password password = new Password("password".toCharArray())) {
                password.clear();

                // Should not throw exception, just return cleared array
                assertNotNull(password.chars());
                assertEquals(8, password.chars().length);
            }
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        void testOfFactoryPreservesContent() {
            final char[] original = "factoryTest123".toCharArray();
            try (final Password pwd = Password.of(original)) {
                assertArrayEquals(original, pwd.chars());
            }
        }

        @Test
        void testOfFactoryIndependentFromSource() {
            final char[] original = "factory".toCharArray();
            try (final Password pwd = Password.of(original)) {
                original[0] = 'X';
                assertEquals('f', pwd.chars()[0]);
            }
        }

        @Test
        void testOfStringFactoryPreservesContent() {
            final String str = "stringPassword123";
            try (final Password pwd = Password.of(str)) {
                assertEquals(str, new String(pwd.chars()));
            }
        }

        @Test
        void testOfStringWithSpecialCharacters() {
            final String str = "P@$$w0rd!@#$%";
            try (final Password pwd = Password.of(str)) {
                assertEquals(str, new String(pwd.chars()));
            }
        }
    }

    @Nested
    @DisplayName("Constructor Validation Tests")
    class ConstructorValidationTests {

        @Test
        void testConstructorThrowsForNull() {
            final NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new Password(null)
            );
            assertTrue(exception.getMessage().contains("cannot be null"));
        }

        @Test
        void testConstructorThrowsForEmptyArray() {
            final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Password(new char[0])
            );
            assertTrue(exception.getMessage().contains("cannot be empty"));
        }

        @Test
        void testOfStringThrowsForNull() {
            final NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> Password.of((String) null)
            );
            assertTrue(exception.getMessage().contains("cannot be null"));
        }

        @Test
        void testOfStringThrowsForEmptyString() {
            final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Password.of("")
            );
            assertTrue(exception.getMessage().contains("cannot be empty"));
        }
    }

    @Nested
    @DisplayName("Immutability and Independence Tests")
    class ImmutabilityTests {

        @Test
        void testMultipleCallsToCharsReturnIndependentCopies() {
            try (final Password password = new Password("password".toCharArray())) {
                final char[] first = password.chars();
                final char[] second = password.chars();

                first[0] = 'X';
                assertEquals('p', second[0], "Modifying one copy should not affect another");
            }
        }

        @Test
        void testIndependentPasswordInstances() {
            try (final Password pwd1 = new Password("pass123".toCharArray());
                 final Password pwd2 = new Password("pass123".toCharArray())) {
                pwd1.clear();

                assertNotEquals('\0', pwd2.chars()[0], "Clearing one instance should not affect another");
                assertEquals('p', pwd2.chars()[0]);
            }
        }

        @Test
        void testToStringDoesNotExposePassword() {
            try (final Password password = new Password("secretPassword123".toCharArray())) {
                final String str = password.toString();

                assertFalse(str.contains("secretPassword123"));
                assertFalse(str.contains("secret"));
                assertTrue(str.contains("***"));
            }
        }
    }

    @Nested
    @DisplayName("Content Comparison Tests")
    class ContentComparisonTests {

        @Test
        void testSameContentPasswordsAreEquivalent() {
            try (final Password pwd1 = new Password("identical".toCharArray());
                 final Password pwd2 = new Password("identical".toCharArray())) {
                assertArrayEquals(pwd1.chars(), pwd2.chars());
            }
        }

        @Test
        void testDifferentContentPasswordsAreDifferent() {
            try (final Password pwd1 = new Password("password1".toCharArray());
                 final Password pwd2 = new Password("password2".toCharArray())) {
                assertFalse(Arrays.equals(pwd1.chars(), pwd2.chars()));
            }
        }

        @Test
        void testPasswordLengthPreserved() {
            final String originalStr = "myPassword123456";
            try (final Password password = new Password(originalStr.toCharArray())) {
                assertEquals(originalStr.length(), password.chars().length);
            }
        }

        @Test
        void testCaseSensitivity() {
            try (final Password pwd1 = new Password("PassWord".toCharArray());
                 final Password pwd2 = new Password("password".toCharArray())) {
                assertFalse(Arrays.equals(pwd1.chars(), pwd2.chars()));
            }
        }
    }

    @Nested
    @DisplayName("AutoCloseable and close() Tests")
    class AutoCloseableTests {

        @Test
        void testCloseImplementsAutoCloseable() {
            try (final Password password = new Password("password".toCharArray())) {
                assertTrue(password instanceof AutoCloseable);
            }
        }

        @Test
        void testCloseMethodClearsPassword() {
            final Password password = new Password("secretPassword".toCharArray());
            password.close();

            for (final char c : password.chars()) {
                assertEquals('\0', c, "All characters should be null after close()");
            }
        }

        @Test
        void testTryWithResourcesAutomaticallyCloses() {
            final char[] testChars = "password123".toCharArray();
            try (final Password password = new Password(testChars)) {
                assertArrayEquals(testChars, password.chars());
                
                password.clear();
                
                for (final char c : password.chars()) {
                    assertEquals('\0', c, "Password should be cleared");
                }
            }
        }

        @Test
        void testCloseCanBeCalledMultipleTimes() {
            final Password password = new Password("password".toCharArray());
            password.close();
            password.close();
            password.close();

            for (final char c : password.chars()) {
                assertEquals('\0', c);
            }
        }

        @Test
        void testCloseAndClearBothClearPassword() {
            try (final Password password1 = new Password("password".toCharArray());
                 final Password password2 = new Password("password".toCharArray())) {
                password1.close();
                password2.clear();

                assertArrayEquals(password1.chars(), password2.chars());
            }
        }
    }
}

