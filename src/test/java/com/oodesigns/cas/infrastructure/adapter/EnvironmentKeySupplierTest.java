package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.KeyPassword;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EnvironmentKeySupplier adapter.
 * Tests environment variable reading and KeyPassword creation.
 */
class EnvironmentKeySupplierTest {

    private EnvironmentKeySupplier keySupplier;
    private Map<String, String> mockEnv;

    @BeforeEach
    void setUp() {
        mockEnv = new HashMap<>();
        keySupplier = new EnvironmentKeySupplier(mockEnv::get);
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
        var result = keySupplier.getPassword("NON_EXISTENT_KEY");
        
        assertTrue(result.isEmpty(), "Should return empty for non-existent env var");
    }

    @Test
    void testGetPasswordReturnsValueForExistingEnvVar() {
        String validKey = "12345678901234567890123456789012"; // 32 chars
        mockEnv.put("TEST_KEY", validKey);

        Optional<KeyPassword> result = keySupplier.getPassword("TEST_KEY");
        
        assertTrue(result.isPresent(), "Should return value for existing env var");
        // We can't easily check the value of KeyPassword as it doesn't expose it directly safely, 
        // but presence confirms it was created.
    }

    @Test
    void testGetPasswordReturnsEmptyForShortKey() {
        String shortKey = "too_short";
        mockEnv.put("SHORT_KEY", shortKey);

        // Suppress the expected warning log for invalid key length
        Logger logger = Logger.getLogger(EnvironmentKeySupplier.class.getName());
        java.util.logging.Level originalLevel = logger.getLevel();
        try {
            logger.setLevel(java.util.logging.Level.SEVERE);

            Optional<KeyPassword> result = keySupplier.getPassword("SHORT_KEY");

            assertTrue(result.isEmpty(), "Should return empty for key shorter than 32 chars");
        } finally {
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void testGetPasswordReturnsEmptyForBlankEnvVar() {
        mockEnv.put("BLANK_KEY", "   ");
        
        Optional<KeyPassword> result = keySupplier.getPassword("BLANK_KEY");
        
        assertTrue(result.isEmpty(), "Should return empty for blank env var value");
    }

    @Test
    void testDefaultConstructor() {
        // Just verify it doesn't crash and is the right type
        EnvironmentKeySupplier defaultSupplier = new EnvironmentKeySupplier();
        assertInstanceOf(EnvironmentKeySupplier.class, defaultSupplier);
    }

    @Test
    void testImplementsKeySupplierInterface() {
        assertInstanceOf(KeySupplier.class, keySupplier);
    }
}
