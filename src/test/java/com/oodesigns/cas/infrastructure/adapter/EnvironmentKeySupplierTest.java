package com.oodesigns.cas.infrastructure.adapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EnvironmentKeySupplier adapter.
 * Tests environment variable reading and KeyPassword creation.
 * <p>
 * Note: These tests verify behavior with null/blank keyIds since
 * environment variables cannot be easily mocked without reflection.
 */
class EnvironmentKeySupplierTest {

    private EnvironmentKeySupplier keySupplier;

    @BeforeEach
    void setUp() {
        keySupplier = new EnvironmentKeySupplier();
    }

    @Test
    void testGetPasswordReturnsEmptyForNullKeyId() {
        var result = keySupplier.getPassword(null);
        
        assertTrue(result.isEmpty(), "Should return empty for null keyId");
    }

    @Test
    void testGetPasswordReturnsEmptyForBlankKeyId() {
        var result = keySupplier.getPassword("   ");
        
        assertTrue(result.isEmpty(), "Should return empty for blank keyId");
    }

    @Test
    void testGetPasswordReturnsEmptyForEmptyKeyId() {
        var result = keySupplier.getPassword("");
        
        assertTrue(result.isEmpty(), "Should return empty for empty keyId");
    }

    @Test
    void testGetPasswordReturnsEmptyForNonExistentEnvVar() {
        // Use a key that definitely doesn't exist
        var result = keySupplier.getPassword("NONEXISTENT_KEY_THAT_SHOULD_NOT_EXIST_12345");
        
        assertTrue(result.isEmpty(), "Should return empty for non-existent env var");
    }

    @Test
    void testGetPasswordReturnsValueForExistingEnvVar() {
        // PATH is typically always set on all systems
        var result = keySupplier.getPassword("PATH");
        
        // PATH should exist and have a value
        if (System.getenv("PATH") != null && !System.getenv("PATH").isBlank()) {
            assertTrue(result.isPresent(), "Should return value for existing env var");
        }
    }

    @Test
    void testImplementsKeySupplierInterface() {
        assertInstanceOf(KeySupplier.class, keySupplier);
    }
}
