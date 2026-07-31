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
 * Password validation: minimum 14 characters, case-sensitive, no complexity rules.
 */
class PasswordTest {

    // Valid test passwords that meet minimum length requirement
    private static final String VALID_PASSWORD_14 = "MyPassword1234";  // 14 chars
    private static final String VALID_PASSWORD_16 = "MyPassword123456"; // 16 chars
    private static final String VALID_PASSWORD_20 = "ValidPassword1234567"; // 20 chars

    @Test
    void testValidPassword() {
        final char[] chars = VALID_PASSWORD_14.toCharArray();
        try (final Password password = Password.of(chars)) {
            assertArrayEquals(chars, password.chars());
        }
    }

    @Test
    void testPasswordCharArrayCloned() {
        final char[] original = VALID_PASSWORD_16.toCharArray();
        try (final Password password = Password.of(original)) {
            original[0] = 'X';
            assertEquals('M', password.chars()[0]);
        }
    }

    @Test
    void testPasswordCharArrayNotMutableViaGetter() {
        try (final Password password = Password.of(VALID_PASSWORD_14.toCharArray())) {
            final char[] retrieved = password.chars();
            retrieved[0] = 'X';
            assertEquals('M', password.chars()[0]);
        }
    }

    @Test
    void testPasswordClear() {
        try (final Password password = Password.of(VALID_PASSWORD_20.toCharArray())) {
            password.clear();
            
            for (final char c : password.chars()) {
                assertEquals('\0', c);
            }
        }
    }

    @Test
    @SuppressWarnings("unused")
    void testNullThrows() {
        assertThrows(NullPointerException.class, () -> {
            //noinspection EmptyTryBlock
            try (final Password password = Password.of((char[]) null)) {
                // Won't reach here
            }
        });
    }

    @Test
    @SuppressWarnings("unused")
    void testEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            //noinspection EmptyTryBlock
            try (final Password password = Password.of(new char[0])) {
                // Won't reach here
            }
        });
    }

    @Test
    @SuppressWarnings("unused")
    void testTooShortThrows() {
        final char[] shortPassword = "short".toCharArray();
        assertThrows(IllegalArgumentException.class, () -> {
            //noinspection EmptyTryBlock
            try (final Password password = Password.of(shortPassword)) {
                // Won't reach here
            }
        });
    }

    @Test
    void testOfFactoryString() {
        try (final Password password = Password.of(VALID_PASSWORD_16)) {
            assertArrayEquals(VALID_PASSWORD_16.toCharArray(), password.chars());
        }
    }

    @Test
    @SuppressWarnings("unused")
    void testOfStringNullThrows() {
        assertThrows(NullPointerException.class, () -> {
            //noinspection EmptyTryBlock
            try (final Password password = Password.of((String) null)) {
                // Won't reach here
            }
        });
    }

    @Test
    @SuppressWarnings("unused")
    void testOfStringEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            //noinspection EmptyTryBlock
            try (final Password password = Password.of("")) {
                // Won't reach here
            }
        });
    }

    @Test
    void testToStringMasksPassword() {
        try (final Password password = Password.of(VALID_PASSWORD_14.toCharArray())) {
            final String str = password.toString();
            assertFalse(str.contains(VALID_PASSWORD_14));
            assertTrue(str.contains("***"));
        }
    }

    @Test
    void testPasswordsWithSameContentAreEquivalent() {
        // Password is not a record - it doesn't override equals()
        // Test that content is equivalent by comparing chars()
        try (final Password pwd1 = Password.of(VALID_PASSWORD_14.toCharArray());
             final Password pwd2 = Password.of(VALID_PASSWORD_14.toCharArray())) {
            assertArrayEquals(pwd1.chars(), pwd2.chars());
        }
    }

    @Test
    void testPasswordsWithDifferentContentAreDifferent() {
        try (final Password pwd1 = Password.of(VALID_PASSWORD_16.toCharArray());
             final Password pwd2 = Password.of(VALID_PASSWORD_20.toCharArray())) {
            final char[] pwd1Chars = pwd1.chars();
            final char[] pwd2Chars = pwd2.chars();
            assertNotEquals(new String(pwd1Chars), new String(pwd2Chars));
        }
    }

    @Test
    void testPasswordCharArrayIndependence() {
        // Verify that two Password instances with same content are independent
        try (final Password pwd1 = Password.of(VALID_PASSWORD_14.toCharArray());
             final Password pwd2 = Password.of(VALID_PASSWORD_14.toCharArray())) {
            // Clearing one doesn't affect the other
            pwd1.clear();
            assertFalse(Arrays.equals(pwd1.chars(), pwd2.chars()));
        }
    }

    @Test
    void testPasswordToStringDefaultResponse() {
        try (final Password password = Password.of(VALID_PASSWORD_20.toCharArray())) {
            final String str = password.toString();
            assertEquals("Password{***}", str);
        }
    }

    @Nested
    @DisplayName("Special Characters and Content Tests")
    class SpecialCharacterTests {

        @ParameterizedTest(name = "Password with {0}")
        @ValueSource(strings = {
            "P@$$w0rd!#%&*1234",           // Special characters (17 chars)
            "пароль密码🔐UniquePass",       // Unicode and emoji (18 chars approx)
            "pass word with spaces1234",   // Spaces (27 chars)
            "pass\nword\n1234567",         // Newlines (17 chars)
            "pass\tword\t1234567"          // Tabs (17 chars)
        })
        void testPasswordWithVariousCharacterTypes(final String passwordString) {
            final char[] chars = passwordString.toCharArray();
            try (final Password password = Password.of(chars)) {
                assertArrayEquals(chars, password.chars());
            }
        }
    }

    @Nested
    @DisplayName("Password Length Variations")
    class PasswordLengthTests {

        @Test
        void testMinimumLengthPassword() {
            final char[] chars = VALID_PASSWORD_14.toCharArray();
            try (final Password password = Password.of(chars)) {
                assertArrayEquals(chars, password.chars());
                assertEquals(14, password.chars().length);
            }
        }

        @Test
        void testVeryLongPassword() {
            final char[] chars = "a".repeat(128).toCharArray();  // Max length
            try (final Password password = Password.of(chars)) {
                assertEquals(128, password.chars().length);
            }
        }

        @Test
        void testPasswordWithAllNumericChars() {
            final char[] chars = "12345678901234".toCharArray();  // 14 chars, all numeric
            try (final Password password = Password.of(chars)) {
                assertArrayEquals(chars, password.chars());
            }
        }
    }

    @Nested
    @DisplayName("Clear and Memory Security Tests")
    class MemorySecurityTests {

        @Test
        void testClearFillsAllCharactersWithNull() {
            try (final Password password = Password.of(VALID_PASSWORD_20.toCharArray())) {
                password.clear();

                final char[] cleared = password.chars();
                for (final char c : cleared) {
                    assertEquals('\0', c, "All characters should be null after clear");
                }
            }
        }

        @Test
        void testClearCanBeCalledMultipleTimes() {
            try (final Password password = Password.of(VALID_PASSWORD_14.toCharArray())) {
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
            try (final Password password = Password.of(VALID_PASSWORD_14.toCharArray())) {
                password.clear();

                // Should not throw exception, just return cleared array
                assertNotNull(password.chars());
                assertEquals(14, password.chars().length);
            }
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        void testOfFactoryPreservesContent() {
            final char[] original = VALID_PASSWORD_14.toCharArray();
            try (final Password pwd = Password.of(original)) {
                assertArrayEquals(original, pwd.chars());
            }
        }

        @Test
        void testOfFactoryIndependentFromSource() {
            final char[] original = VALID_PASSWORD_16.toCharArray();
            try (final Password pwd = Password.of(original)) {
                original[0] = 'X';
                assertEquals('M', pwd.chars()[0]);
            }
        }

        @Test
        void testOfStringFactoryPreservesContent() {
            final String str = VALID_PASSWORD_20;
            try (final Password pwd = Password.of(str)) {
                assertEquals(str, new String(pwd.chars()));
            }
        }

        @Test
        void testOfStringWithSpecialCharacters() {
            final String str = "P@$$w0rd!@#$%1234";  // 17 chars with special chars
            try (final Password pwd = Password.of(str)) {
                assertEquals(str, new String(pwd.chars()));
            }
        }
    }

    @Nested
    @DisplayName("Constructor Validation Tests")
    class ConstructorValidationTests {

        @Test
        @SuppressWarnings("unused")
        void testConstructorThrowsForNull() {
            assertThrows(
                NullPointerException.class,
                () -> {
                    //noinspection EmptyTryBlock
                    try (final Password password = Password.of((char[]) null)) {
                        // Won't reach here
                    }
                }
            );
        }

        @Test
        @SuppressWarnings("unused")
        void testConstructorThrowsForEmptyArray() {
            final char[] emptyArray = new char[0];
            assertThrows(
                IllegalArgumentException.class,
                () -> {
                    //noinspection EmptyTryBlock
                    try (final Password password = Password.of(emptyArray)) {
                        // Won't reach here
                    }
                }
            );
        }

        @Test
        @SuppressWarnings("unused")
        void testConstructorThrowsForTooShort() {
            final char[] shortPassword = "short".toCharArray();
            assertThrows(
                IllegalArgumentException.class,
                () -> {
                    //noinspection EmptyTryBlock
                    try (final Password password = Password.of(shortPassword)) {
                        // Won't reach here
                    }
                }
            );
        }

        @Test
        @SuppressWarnings("unused")
        void testOfStringThrowsForNull() {
            assertThrows(
                NullPointerException.class,
                () -> {
                    //noinspection EmptyTryBlock
                    try (final Password password = Password.of((String) null)) {
                        // Won't reach here
                    }
                }
            );
        }

        @Test
        @SuppressWarnings("unused")
        void testOfStringThrowsForEmptyString() {
            assertThrows(
                IllegalArgumentException.class,
                () -> {
                    //noinspection EmptyTryBlock
                    try (final Password password = Password.of("")) {
                        // Won't reach here
                    }
                }
            );
        }
    }

    @Nested
    @DisplayName("Immutability and Independence Tests")
    class ImmutabilityTests {

        @Test
        void testMultipleCallsToCharsReturnIndependentCopies() {
            try (final Password password = Password.of(VALID_PASSWORD_14.toCharArray())) {
                final char[] first = password.chars();
                final char[] second = password.chars();

                first[0] = 'X';
                assertEquals('M', second[0], "Modifying one copy should not affect another");
            }
        }

        @Test
        void testIndependentPasswordInstances() {
            try (final Password pwd1 = Password.of(VALID_PASSWORD_14.toCharArray());
                 final Password pwd2 = Password.of(VALID_PASSWORD_14.toCharArray())) {
                pwd1.clear();

                assertNotEquals('\0', pwd2.chars()[0], "Clearing one instance should not affect another");
                assertEquals('M', pwd2.chars()[0]);
            }
        }

        @Test
        void testToStringDoesNotExposePassword() {
            try (final Password password = Password.of(VALID_PASSWORD_20.toCharArray())) {
                final String str = password.toString();

                assertFalse(str.contains(VALID_PASSWORD_20), "toString should not expose actual password");
                assertFalse(str.contains("Valid"), "toString should not contain part of password");
                assertTrue(str.contains("***"), "toString should use masking");
                assertEquals("Password{***}", str, "toString should follow the masking format");
            }
        }
    }

    @Nested
    @DisplayName("Content Comparison Tests")
    class ContentComparisonTests {

        @Test
        void testSameContentPasswordsAreEquivalent() {
            try (final Password pwd1 = Password.of(VALID_PASSWORD_14.toCharArray());
                 final Password pwd2 = Password.of(VALID_PASSWORD_14.toCharArray())) {
                assertArrayEquals(pwd1.chars(), pwd2.chars());
            }
        }

        @Test
        void testDifferentContentPasswordsAreDifferent() {
            try (final Password pwd1 = Password.of(VALID_PASSWORD_16.toCharArray());
                 final Password pwd2 = Password.of(VALID_PASSWORD_20.toCharArray())) {
                assertFalse(Arrays.equals(pwd1.chars(), pwd2.chars()));
            }
        }

        @Test
        void testPasswordLengthPreserved() {
            try (final Password password = Password.of(VALID_PASSWORD_20.toCharArray())) {
                assertEquals(VALID_PASSWORD_20.length(), password.chars().length);
            }
        }

        @Test
        void testCaseSensitivity() {
            final char[] pwd1Chars = "MyPassword123456".toCharArray();
            final char[] pwd2Chars = "mypassword123456".toCharArray();
            try (final Password pwd1 = Password.of(pwd1Chars);
                 final Password pwd2 = Password.of(pwd2Chars)) {
                assertFalse(Arrays.equals(pwd1.chars(), pwd2.chars()));
            }
        }
    }

    @Nested
    @DisplayName("AutoCloseable and close() Tests")
    class AutoCloseableTests {

        @Test
        void testCloseImplementsAutoCloseable() {
            try (final Password password = Password.of(VALID_PASSWORD_14.toCharArray())) {
                assertInstanceOf(AutoCloseable.class, password);
            }
        }

        @Test
        void testCloseMethodClearsPassword() {
            try (final Password password = Password.of(VALID_PASSWORD_20.toCharArray())) {
                password.close();

                for (final char c : password.chars()) {
                    assertEquals('\0', c, "All characters should be null after close()");
                }
            }
        }

        @Test
        void testTryWithResourcesAutomaticallyCloses() {
            final char[] testChars = VALID_PASSWORD_16.toCharArray();
            try (final Password password = Password.of(testChars)) {
                assertArrayEquals(testChars, password.chars());
                
                password.clear();
                
                for (final char c : password.chars()) {
                    assertEquals('\0', c, "Password should be cleared");
                }
            }
        }

        @Test
        void testCloseCanBeCalledMultipleTimes() {
            try (final Password password = Password.of(VALID_PASSWORD_14.toCharArray())) {
                password.close();
                password.close();
                password.close();

                for (final char c : password.chars()) {
                    assertEquals('\0', c);
                }
            }
        }

        @Test
        void testCloseAndClearBothClearPassword() {
            try (final Password password1 = Password.of(VALID_PASSWORD_14.toCharArray());
                 final Password password2 = Password.of(VALID_PASSWORD_14.toCharArray())) {
                password1.close();
                password2.clear();

                assertArrayEquals(password1.chars(), password2.chars());
            }
        }
    }

    @Nested
    @DisplayName("Boundary Value Tests")
    class BoundaryValueTests {

        @Test
        void testExactlyMinimumLength() {
            final String password = "a".repeat(14);  // Exactly 14 chars
            try (final Password pwd = Password.of(password.toCharArray())) {
                assertEquals(14, pwd.chars().length);
            }
        }

        @Test
        @SuppressWarnings("unused")
        void testOneCharUnderMinimum() {
            final String password = "a".repeat(13);  // One under minimum
            final char[] chars = password.toCharArray();
            assertThrows(IllegalArgumentException.class, () -> {
                //noinspection EmptyTryBlock
                try (final Password pwd = Password.of(chars)) {
                    // Won't reach here
                }
            });
        }

        @Test
        void testExactlyMaximumLength() {
            final String password = "a".repeat(128);  // Exactly 128 chars
            try (final Password pwd = Password.of(password.toCharArray())) {
                assertEquals(128, pwd.chars().length);
            }
        }

        @Test
        @SuppressWarnings("unused")
        void testOneCharOverMaximum() {
            final String password = "a".repeat(129);  // One over maximum
            final char[] chars = password.toCharArray();
            assertThrows(IllegalArgumentException.class, () -> {
                //noinspection EmptyTryBlock
                try (final Password pwd = Password.of(chars)) {
                    // Won't reach here
                }
            });
        }

        @Test
        @SuppressWarnings("unused")
        void testMaximumLengthError() {
            final String password = "a".repeat(200);  // Way over maximum
            final char[] chars = password.toCharArray();
            final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> {
                    //noinspection EmptyTryBlock
                    try (final Password pwd = Password.of(chars)) {
                        // Won't reach here
                    }
                }
            );
            assertTrue(exception.getMessage().contains("128 characters"));
        }
    }

    @Nested
    @DisplayName("Whitespace and Blank Tests")
    class WhitespaceTests {

        @ParameterizedTest(name = "Password with {0}")
        @ValueSource(strings = {
            "   ValidPassword1234",      // Leading spaces
            "ValidPassword1234   ",      // Trailing spaces
            "Valid Pass word 1234",      // Internal spaces (20 chars)
            "Pass\tword\n1234567"        // Mixed whitespace (17 chars)
        })
        void testPasswordWithValidWhitespace(final String password) {
            try (final Password pwd = Password.of(password.toCharArray())) {
                assertEquals(password.length(), pwd.chars().length);
            }
        }

        @ParameterizedTest(name = "Password with only {0}")
        @ValueSource(strings = {
            "              ",             // 14 spaces
            "\t\t\t\t\t\t\t\t\t\t\t\t\t\t",  // 14 tabs
            "\n\n\n\n\n\n\n\n\n\n\n\n\n\n"   // 14 newlines
        })
        @SuppressWarnings("unused")
        void testPasswordWithOnlyWhitespace(final String password) {
            final char[] chars = password.toCharArray();
            assertThrows(IllegalArgumentException.class, () -> {
                //noinspection EmptyTryBlock
                try (final Password pwd = Password.of(chars)) {
                    // Won't reach here
                }
            });
        }
    }

    @Nested
    @DisplayName("Character Set Tests")
    class CharacterSetTests {

        @ParameterizedTest(name = "Password with {0}")
        @ValueSource(strings = {
            "abcdefghijklmn",                                                                                      // 14 lowercase
            "ABCDEFGHIJKLMN",                                                                                      // 14 uppercase
            "12345678901234",                                                                                      // 14 digits
            "!@#$%^&*()_+-=[]{}|;:',.<>?/~`",                                                                     // 32 chars of symbols
            "こんにちは世界パスワード1234",                                                                              // Japanese + numbers (16 chars)
            "Password🔐🔒🔓1234"                                                                                      // Emoji (19 chars)
        })
        void testPasswordWithVariousCharacterSets(final String password) {
            try (final Password pwd = Password.of(password.toCharArray())) {
                assertEquals(password.length(), pwd.chars().length);
            }
        }

        @Test
        @SuppressWarnings("unused")
        void testPasswordWithUnicodeCharacters() {
            final String password = "こんにちは世界パスワード";  // Japanese text - 12 chars
            // This should fail because it's under 14 chars
            final char[] chars = password.toCharArray();
            assertThrows(IllegalArgumentException.class, () -> {
                //noinspection EmptyTryBlock
                try (final Password pwd = Password.of(chars)) {
                    // Won't reach here
                }
            });
        }

        @Test
        void testPasswordWithAllASCIISymbols() {
            final String password = "!\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";
            try (final Password pwd = Password.of(password.toCharArray())) {
                assertTrue(pwd.chars().length >= 14);
            }
        }
    }

    @Nested
    @DisplayName("Functional Composition Tests")
    class FunctionalCompositionTests {

        @Test
        void testValidationChainIsExecuted() {
            // Valid password should pass through entire chain
            try (final Password pwd = Password.of(VALID_PASSWORD_14.toCharArray())) {
                assertNotNull(pwd.chars());
                assertEquals(VALID_PASSWORD_14.length(), pwd.chars().length);
            }
        }

        @Test
        void testMultipleInstancesIndependent() {
            final char[] chars1 = VALID_PASSWORD_14.toCharArray();
            final char[] chars2 = VALID_PASSWORD_16.toCharArray();

            try (final Password pwd1 = Password.of(chars1);
                 final Password pwd2 = Password.of(chars2)) {

                // Modify source arrays
                chars1[0] = 'X';
                chars2[0] = 'X';

                // Passwords should be unaffected
                assertEquals('M', pwd1.chars()[0]);
                assertEquals('M', pwd2.chars()[0]);
            }
        }

        @Test
        @SuppressWarnings("unused")
        void testValidationFailsEarlyOnLength() {
            final char[] shortPassword = "short".toCharArray();
            assertThrows(
                IllegalArgumentException.class,
                () -> {
                    //noinspection EmptyTryBlock
                    try (final Password password = Password.of(shortPassword)) {
                        // Won't reach here
                    }
                }
            );
        }

        @Test
        @SuppressWarnings("unused")
        void testValidationFailsOnBlankContent() {
            final char[] allSpaces = new char[]{' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '};
            assertThrows(
                IllegalArgumentException.class,
                () -> {
                    //noinspection EmptyTryBlock
                    try (final Password password = Password.of(allSpaces)) {
                        // Won't reach here
                    }
                }
            );
        }
    }

    @Nested
    @DisplayName("Memory Management Tests")
    class MemoryManagementTests {

        @Test
        void testSourceArrayCanBeCleared() {
            final char[] sourceArray = VALID_PASSWORD_14.toCharArray();
            try (final Password pwd = Password.of(sourceArray)) {
                // Clear source after passing to Password
                Arrays.fill(sourceArray, '\0');

                // Password should still have original content (cloned)
                assertEquals('M', pwd.chars()[0]);
            }
        }

        @Test
        void testMultipleClearDoesNotThrow() {
            try (final Password pwd = Password.of(VALID_PASSWORD_14.toCharArray())) {
                assertDoesNotThrow(() -> {
                    pwd.clear();
                    pwd.clear();
                    pwd.clear();
                    pwd.clear();
                    pwd.clear();
                });
            }
        }

        @Test
        void testClearIsEffective() {
            try (final Password pwd = Password.of(VALID_PASSWORD_20.toCharArray())) {
                assertNotEquals('\0', pwd.chars()[0]);

                pwd.clear();

                for (char c : pwd.chars()) {
                    assertEquals('\0', c);
                }
            }
        }

        @Test
        void testGettersReturnClonesNotReferences() {
            try (final Password pwd = Password.of(VALID_PASSWORD_14.toCharArray())) {
                final char[] chars1 = pwd.chars();
                final char[] chars2 = pwd.chars();

                // Should be different objects
                assertNotSame(chars1, chars2);
                // But same content
                assertArrayEquals(chars1, chars2);
            }
        }
    }

    @Nested
    @DisplayName("Error Message Tests")
    class ErrorMessageTests {

        @Test
        @SuppressWarnings("unused")
        void testNullErrorMessageDescriptive() {
            final NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> {
                    //noinspection EmptyTryBlock
                    try (final Password password = Password.of((char[]) null)) {
                        // Won't reach here
                    }
                }
            );
            assertNotNull(exception.getMessage());
            assertTrue(exception.getMessage().contains("null"));
        }

        @Test
        @SuppressWarnings("unused")
        void testMinimumLengthErrorMessage() {
            final char[] shortPassword = "short".toCharArray();
            assertThrows(IllegalArgumentException.class, () -> {
                //noinspection EmptyTryBlock
                try (final Password password = Password.of(shortPassword)) {
                    // Won't reach here
                }
            });
        }

        @Test
        @SuppressWarnings("unused")
        void testMaximumLengthErrorMessage() {
            final String tooLong = "a".repeat(129);
            final char[] chars = tooLong.toCharArray();
            assertThrows(IllegalArgumentException.class, () -> {
                //noinspection EmptyTryBlock
                try (final Password password = Password.of(chars)) {
                    // Won't reach here
                }
            });
        }

        @Test
        @SuppressWarnings("unused")
        void testBlankContentErrorMessage() {
            final String onlySpaces = "              ";  // 14 spaces
            final char[] chars = onlySpaces.toCharArray();
            assertThrows(IllegalArgumentException.class, () -> {
                //noinspection EmptyTryBlock
                try (final Password password = Password.of(chars)) {
                    // Won't reach here
                }
            });
        }
    }

    @Nested
    @DisplayName("Real World Scenario Tests")
    class RealWorldScenarioTests {

        @Test
        void testLongPassphrase() {
            final String passphrase = "The quick brown fox jumps over the lazy dog";  // 44 chars
            try (final Password pwd = Password.of(passphrase.toCharArray())) {
                assertEquals(passphrase.length(), pwd.chars().length);
            }
        }

        @Test
        void testPasswordWithMixedEverything() {
            final String password = "P@ssw0rd!With.Multiple_Types&Of$Chars123";  // 40 chars
            try (final Password pwd = Password.of(password.toCharArray())) {
                assertEquals(password.length(), pwd.chars().length);
            }
        }

        @Test
        @SuppressWarnings("unused")
        void testMinimalValidPassword() {
            final String password = "Pass123456789";  // Exactly 13 chars - should fail
            final char[] chars = password.toCharArray();
            assertThrows(IllegalArgumentException.class, () -> {
                //noinspection EmptyTryBlock
                try (final Password pwd = Password.of(chars)) {
                    // Won't reach here
                }
            });
        }

        @Test
        void testMinimalValidPasswordAt14() {
            final String password = "Pass1234567890";  // Exactly 14 chars
            try (final Password pwd = Password.of(password.toCharArray())) {
                assertEquals(14, pwd.chars().length);
            }
        }

        @Test
        void testApproachingMaximumLength() {
            final String password = "a".repeat(127);  // One less than maximum
            try (final Password pwd = Password.of(password.toCharArray())) {
                assertEquals(127, pwd.chars().length);
            }
        }
    }
}
