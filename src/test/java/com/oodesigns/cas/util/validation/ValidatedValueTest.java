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
    static class TestValidatedValue extends ValidatedValue<String, String> {
        TestValidatedValue(final String raw) {
            super(raw);
        }

        @Override
        protected String parse(final String raw) {
            return raw.trim().toLowerCase();
        }

        @Override
        protected String validate(final String value) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Value cannot be empty");
            }
            return value;
        }
    }

    @Test
    void testValueReturnsValidatedValue() {
        final var validated = new TestValidatedValue("  HELLO  ");
        assertEquals("hello", validated.value());
    }

    @Test
    void testToStringReturnsStringValueOfValue() {
        final var validated = new TestValidatedValue("World");
        assertEquals("world", validated.toString());
    }

    @Test
    void testEqualsReturnsTrueForSameValue() {
        final var v1 = new TestValidatedValue("test");
        final var v2 = new TestValidatedValue("TEST");
        
        assertEquals(v1, v2);
    }

    @Test
    void testEqualsReturnsFalseForDifferentValues() {
        final var v1 = new TestValidatedValue("one");
        final var v2 = new TestValidatedValue("two");
        
        assertNotEquals(v1, v2);
    }

    @Test
    void testEqualsReturnsFalseForNull() {
        final var v1 = new TestValidatedValue("test");
        
        assertNotEquals(null, v1);
    }

    @Test
    void testEqualsReturnsFalseForDifferentClass() {
        final var v1 = new TestValidatedValue("test");
        final Object other = "test";
        
        assertNotEquals(other, v1);
    }

    @Test
    void testEqualsReturnsFalseForDifferentSubclass() {
        final var v1 = new TestValidatedValue("test");
        final Object v2 = new ValidatedValue<String, String>("test") {
            @Override
            protected String parse(final String raw) {
                return raw.toLowerCase();
            }

            @Override
            protected String validate(final String value) {
                return value;
            }
        };
        
        // Different classes should not be equal even with same value
        assertNotEquals(v2, v1);
    }

    @Test
    void testHashCodeConsistentWithEquals() {
        final var v1 = new TestValidatedValue("test");
        final var v2 = new TestValidatedValue("TEST");
        
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void testHashCodeDifferentForDifferentValues() {
        final var v1 = new TestValidatedValue("one");
        final var v2 = new TestValidatedValue("two");
        
        // While not strictly required, different values should typically have different hash codes
        assertNotEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void testValidationThrowsForInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> new TestValidatedValue("   "));
    }

    @Test
    void testEqualsWithSameInstance() {
        final var v1 = new TestValidatedValue("test");
        
        // Same instance should be equal to itself
        assertEquals(v1, v1);
    }

    @Test
    void testEqualsSymmetry() {
        final var v1 = new TestValidatedValue("abc");
        final var v2 = new TestValidatedValue("ABC");
        
        // Symmetric: if v1.equals(v2) then v2.equals(v1)
        assertEquals(v1, v2);
        assertEquals(v2, v1);
    }

    @Test
    void testEqualsWithObjectExplicitlyNull() {
        final var v1 = new TestValidatedValue("test");
        final Object nullObj = null;
        
        assertNotEquals(v1, nullObj);
    }

    @Test
    void testEqualsWithDifferentValidatedValueSubtype() {
        final var v1 = new TestValidatedValue("hello");
        
        // Create another ValidatedValue subclass with the same underlying value
        final ValidatedValue<String, String> v2 = new ValidatedValue<>("hello") {
            @Override
            protected String parse(final String raw) {
                return raw;
            }

            @Override
            protected String validate(final String value) {
                return value;
            }
        };
        
        // Different classes, so not equal even if values match
        assertNotEquals(v1, v2);
    }
}
