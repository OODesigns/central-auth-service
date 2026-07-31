package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Nested
    @DisplayName("Valid Bcrypt Format Tests")
    class ValidBcryptTests {

        @Test
        void testValidBcrypt2A() {
            final PasswordHash hash = PasswordHash.of(BCRYPT_2A);
            assertEquals(BCRYPT_2A, hash.value());
        }

        @Test
        void testValidBcrypt2B() {
            final PasswordHash hash = PasswordHash.of(BCRYPT_2B);
            assertEquals(BCRYPT_2B, hash.value());
        }

        @Test
        void testValidBcrypt2Y() {
            final PasswordHash hash = PasswordHash.of(BCRYPT_2Y);
            assertEquals(BCRYPT_2Y, hash.value());
        }

        @Test
        void testDifferentRoundCounts() {
            final String bcrypt10 = "$2a$10$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";
            final String bcrypt14 = "$2b$14$R9h/cIPz0gi.URNNW3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";
            
            assertDoesNotThrow(() -> PasswordHash.of(bcrypt10));
            assertDoesNotThrow(() -> PasswordHash.of(bcrypt14));
        }
    }

    @Nested
    @DisplayName("Invalid Format Tests")
    class InvalidFormatTests {

        @Test
        void testInvalidPrefixThrows() {
            assertThrows(IllegalArgumentException.class, () -> PasswordHash.of("$2$12$invalid"));
        }

        @Test
        void testPlaintextThrows() {
            assertThrows(IllegalArgumentException.class, () -> PasswordHash.of("plaintext_password"));
        }

        @ParameterizedTest(name = "Null/empty/blank input: {0}")
        @ValueSource(strings = {"", "   "})
        void testNullEmptyBlankThrows(final String invalidInput) {
            final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> PasswordHash.of(invalidInput)
            );
            assertTrue(ex.getMessage().contains("cannot be blank"));
        }

        @Test
        void testNullThrows() {
            final NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> PasswordHash.of(null)
            );
            assertTrue(ex.getMessage().contains("cannot be null"));
        }

        @ParameterizedTest(name = "Invalid format: {0}")
        @ValueSource(strings = {
            "$1a$12$invalid",           // Wrong version (1)
            "$3b$12$invalid",           // Wrong version (3)
            "$2a$12invalid",            // Missing $
            "$2a12$invalid",            // Missing $
            "$2a$invalid$",             // Missing round count
            "$2a$ab$invalid",           // Non-numeric round
            "$2a$12$short",             // Too short hash part
            "not_bcrypt_at_all",        // Completely invalid
            "2a$12$valid_looking_but_missing_prefix"
        })
        void testVariousInvalidFormats(final String invalidHash) {
            assertThrows(
                IllegalArgumentException.class,
                () -> PasswordHash.of(invalidHash),
                "Should reject invalid format: " + invalidHash
            );
        }
    }

    @Nested
    @DisplayName("Security and Masking Tests")
    class SecurityTests {

        @Test
        void testToStringMasksPassword() {
            final PasswordHash hash = PasswordHash.of(BCRYPT_2A);
            final String str = hash.toString();
            assertFalse(str.contains(BCRYPT_2A));
            assertTrue(str.contains("***"));
        }

        @Test
        void testDisplayValueMasksPassword() {
            final PasswordHash hash = PasswordHash.of(BCRYPT_2A);
            final String display = hash.getDisplayValue();
            assertFalse(display.contains(BCRYPT_2A));
            assertTrue(display.contains("****"));
        }

        @Test
        void testValueAccessReturnsActualHash() {
            final PasswordHash hash = PasswordHash.of(BCRYPT_2A);
            assertEquals(BCRYPT_2A, hash.value());
        }
    }

    @Nested
    @DisplayName("Equality and Hashing Tests")
    class EqualityTests {

        @Test
        void testEqualityBasedOnHash() {
            final PasswordHash hash1 = PasswordHash.of(BCRYPT_2A);
            final PasswordHash hash2 = PasswordHash.of(BCRYPT_2A);
            assertEquals(hash1, hash2);
        }

        @Test
        void testInequalityDifferentHashes() {
            final PasswordHash hash1 = PasswordHash.of(BCRYPT_2A);
            final PasswordHash hash2 = PasswordHash.of(BCRYPT_2B);
            assertNotEquals(hash1, hash2);
        }

        @Test
        void testHashCodeConsistency() {
            final PasswordHash hash1 = PasswordHash.of(BCRYPT_2A);
            final PasswordHash hash2 = PasswordHash.of(BCRYPT_2A);
            assertEquals(hash1.hashCode(), hash2.hashCode());
        }

        @Test
        void testHashCodeDifferentForDifferentHashes() {
            final PasswordHash hash1 = PasswordHash.of(BCRYPT_2A);
            final PasswordHash hash2 = PasswordHash.of(BCRYPT_2B);
            assertNotEquals(hash1.hashCode(), hash2.hashCode());
        }

        @Test
        void testEqualityIsSymmetric() {
            final PasswordHash hash1 = PasswordHash.of(BCRYPT_2A);
            final PasswordHash hash2 = PasswordHash.of(BCRYPT_2A);
            assertEquals(hash1, hash2);
            assertEquals(hash2, hash1);
        }

        @Test
        void testEqualityIsTransitive() {
            final PasswordHash hash1 = PasswordHash.of(BCRYPT_2A);
            final PasswordHash hash2 = PasswordHash.of(BCRYPT_2A);
            final PasswordHash hash3 = PasswordHash.of(BCRYPT_2A);
            assertEquals(hash1, hash2);
            assertEquals(hash2, hash3);
            assertEquals(hash1, hash3);
        }

        @Test
        void testNotEqualToNull() {
            final PasswordHash hash = PasswordHash.of(BCRYPT_2A);
            assertNotEquals(null, hash);
        }

        @Test
        @SuppressWarnings("AssertBetweenInconvertibleTypes")
        void testNotEqualToDifferentType() {
            final PasswordHash hash = PasswordHash.of(BCRYPT_2A);
            assertNotEquals(BCRYPT_2A, hash);
            assertNotEquals(123, hash);
        }
    }

    @Nested
    @DisplayName("Immutability Tests")
    class ImmutabilityTests {

        @Test
        void testValueIsImmutable() {
            final PasswordHash hash = PasswordHash.of(BCRYPT_2A);
            final String value1 = hash.value();
            final String value2 = hash.value();
            assertEquals(value1, value2);
        }

        @Test
        void testInstancesAreIndependent() {
            final PasswordHash hash1 = PasswordHash.of(BCRYPT_2A);
            final PasswordHash hash2 = PasswordHash.of(BCRYPT_2B);
            
            assertEquals(BCRYPT_2A, hash1.value());
            assertEquals(BCRYPT_2B, hash2.value());
        }
    }
}

