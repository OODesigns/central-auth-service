package com.oodesigns.cas.util.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidatedValue abstract base class.
 * Tests: toString(), equals(), hashCode() contract.
 */
class ValidatedValueTest {

    /**
     * Test implementation of ValidatedValue for testing purposes.
     */
    static class TestValidatedValue extends ValidatedValue<String> {
        TestValidatedValue(final String validated) {
            super(validated);
        }

        /**
         * Factory method to create a test value.
         * Performs parsing and validation before construction.
         * 
         * @param raw the raw input string
         * @return TestValidatedValue instance
         * @throws IllegalArgumentException if value becomes empty after processing
         */
        static TestValidatedValue of(final String raw) {
            final String parsed = raw.trim().toLowerCase();
            if (parsed.isEmpty()) {
                throw new IllegalArgumentException("Value cannot be empty");
            }
            return new TestValidatedValue(parsed);
        }
    }

    @Test
    void testValueReturnsValidatedValue() {
        final var validated = TestValidatedValue.of("  HELLO  ");
        assertEquals("hello", validated.value());
    }

    @Test
    void testToStringReturnsStringValueOfValue() {
        final var validated = TestValidatedValue.of("World");
        assertEquals("world", validated.toString());
    }

    @Test
    void testEqualsReturnsTrueForSameValue() {
        final var v1 = TestValidatedValue.of("test");
        final var v2 = TestValidatedValue.of("TEST");
        
        assertEquals(v1, v2);
    }

    @Test
    void testEqualsReturnsFalseForDifferentValues() {
        final var v1 = TestValidatedValue.of("one");
        final var v2 = TestValidatedValue.of("two");
        
        assertNotEquals(v1, v2);
    }

    @Test
    void testEqualsReturnsFalseForNull() {
        final var v1 = TestValidatedValue.of("test");
        
        // Explicitly call equals(null) to test the null check in equals()
        assertFalse(v1.equals(null));
    }

    @Test
    void testEqualsReturnsFalseForDifferentClass() {
        final var v1 = TestValidatedValue.of("test");
        final Object other = "test";
        
        assertNotEquals(other, v1);
    }

    @Test
    void testEqualsReturnsFalseForDifferentSubclass() {
        final var v1 = TestValidatedValue.of("test");
        final Object v2 = new ValidatedValue<>("test") {};

        // Different classes should not be equal even with same value
        assertNotEquals(v2, v1);
    }

    @Test
    void testHashCodeConsistentWithEquals() {
        final var v1 = TestValidatedValue.of("test");
        final var v2 = TestValidatedValue.of("TEST");
        
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void testHashCodeDifferentForDifferentValues() {
        final var v1 = TestValidatedValue.of("one");
        final var v2 = TestValidatedValue.of("two");
        
        // While not strictly required, different values should typically have different hash codes
        assertNotEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void testValidationThrowsForInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> TestValidatedValue.of("   "));
    }

    @Test
    @SuppressWarnings("EqualsWithItself")
    void testEqualsWithSameInstance() {
        final var v1 = TestValidatedValue.of("test");
        
        // Same instance should be equal to itself (reflexivity property)
        assertEquals(v1, v1);
    }

    @Test
    void testEqualsSymmetry() {
        final var v1 = TestValidatedValue.of("abc");
        final var v2 = TestValidatedValue.of("ABC");
        
        // Symmetric: if v1.equals(v2) then v2.equals(v1)
        assertEquals(v1, v2);
        assertEquals(v2, v1);
    }


    @Test
    void testEqualsWithDifferentValidatedValueSubtype() {
        final var v1 = TestValidatedValue.of("hello");
        
        // Create another ValidatedValue subclass with the same underlying value
        final ValidatedValue<String> v2 = new ValidatedValue<>("hello") {};
        
        // Different classes, so not equal even if values match
        assertNotEquals(v1, v2);
    }
}
