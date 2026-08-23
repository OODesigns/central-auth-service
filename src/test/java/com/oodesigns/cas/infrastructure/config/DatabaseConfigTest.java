package com.oodesigns.cas.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import com.oodesigns.cas.util.file.FileLoaderProviderFactory;
import com.oodesigns.cas.util.properties.EnvironmentVariableTransformer;
import com.oodesigns.cas.util.properties.PropertiesReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseConfig.
 * Tests property loading, validation, and type conversion with dependency injection.
 */
class DatabaseConfigTest {
    
    @BeforeEach
    void setUp() {
        System.setProperty("DB_HOST", "test-host");
        System.setProperty("DB_PORT", "5433");
        System.setProperty("DB_USER", "app_user");
        System.setProperty("APP_PASSWORD", "SecureP@ss123");
    }
    
    @AfterEach
    void tearDown() {
        System.clearProperty("DB_HOST");
        System.clearProperty("DB_PORT");
        System.clearProperty("DB_NAME");
        System.clearProperty("DB_USER");
        System.clearProperty("APP_DB");
        System.clearProperty("APP_PASSWORD");
    }
    
    private DatabaseConfig createConfig() {
        // Create a test PropertiesReader that uses environment variables with defaults
        final PropertiesReader reader = new PropertiesReader(
            "application.properties",
            new EnvironmentVariableTransformer(),
            FileLoaderProviderFactory.defaultProvider()
        );
        return new DatabaseConfig(reader);
    }
    
    @Test
    void testPropertyResolutionWithSystemProperty() {
        final DatabaseConfig config = createConfig();
        
        assertEquals("test-host", config.getHost());
        assertEquals(5433, config.getPort());
    }
    
    @Test
    void testPropertyResolutionWithDefault() {
        final DatabaseConfig config = createConfig();
        
        assertEquals("auth_db", config.getDatabaseName());
        assertEquals(DatabaseUser.of("app_user"), config.getUsername());
    }
    
    @Test
    void testInvalidPortThrowsInConstructor() {
        System.setProperty("DB_PORT", "99999");
        
        assertThrows(IllegalArgumentException.class, this::createConfig);
    }
    
    @Test
    void testInvalidHostThrowsInConstructor() {
        System.setProperty("DB_HOST", "invalid..host");
        
        assertThrows(IllegalArgumentException.class, this::createConfig);
    }
    
    @Test
    void testInvalidDatabaseNameThrowsInConstructor() {
        System.setProperty("APP_DB", "123invalid");
        
        assertThrows(IllegalArgumentException.class, this::createConfig);
    }
    
    @Test
    void testValidPortRange() {
        System.setProperty("DB_PORT", "1");
        assertDoesNotThrow(this::createConfig);
        
        System.setProperty("DB_PORT", "65535");
        assertDoesNotThrow(this::createConfig);
    }
    
    @Test
    void testConfigIsImmutableAfterConstruction() {
        final DatabaseConfig config = createConfig();
        final String host1 = config.getHost();
        
        System.setProperty("DB_HOST", "different_host");
        
        final String host2 = config.getHost();
        assertEquals(host1, host2);
    }
    
    @Test
    void testMultipleConfigInstancesAreIndependent() {
        final DatabaseConfig config1 = createConfig();
        
        System.setProperty("DB_HOST", "new-host");
        final DatabaseConfig config2 = createConfig();
        
        assertEquals("test-host", config1.getHost());
        assertEquals("new-host", config2.getHost());
    }
    
    @Test
    void testPasswordValidation() {
        System.setProperty("APP_PASSWORD", "ValidP@ss1");
        
        final DatabaseConfig config = createConfig();
        try (final DatabasePassword password = config.getPassword()) {
            assertArrayEquals("ValidP@ss1".toCharArray(), password.chars());
        }
    }

    @Test
    void testInvalidPasswordThrowsInConstructor() {
        System.setProperty("APP_PASSWORD", "weak");
        
        assertThrows(IllegalArgumentException.class, this::createConfig);
    }

    @Test
    void testBlankHostThrowsInConstructor() {
        System.setProperty("DB_HOST", "   ");
        
        assertThrows(IllegalArgumentException.class, this::createConfig);
    }

    @Test
    void testBlankPasswordThrowsInConstructor() {
        System.setProperty("APP_PASSWORD", "   ");
        
        assertThrows(IllegalArgumentException.class, this::createConfig);
    }
}
