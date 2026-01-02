package com.oodesigns.cas.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseConfig.
 * Tests property loading, validation, and explicit property definitions.
 */
class DatabaseConfigTest {
    
    @BeforeEach
    void setUp() {
        System.setProperty("DB_HOST", "testhost");
        System.setProperty("DB_PORT", "5433");
        System.setProperty("APP_PASSWORD", "SecureP@ss123");
    }
    
    @AfterEach
    void tearDown() {
        System.clearProperty("DB_HOST");
        System.clearProperty("DB_PORT");
        System.clearProperty("APP_DB");
        System.clearProperty("APP_USER");
        System.clearProperty("APP_PASSWORD");
    }
    
    @Test
    void testPropertyResolutionWithSystemProperty() {
        DatabaseConfig config = new DatabaseConfig();
        
        assertEquals("testhost", config.getHost());
        assertEquals(5433, config.getPort());
    }
    
    @Test
    void testPropertyResolutionWithDefault() {
        DatabaseConfig config = new DatabaseConfig();
        
        assertEquals("auth_db", config.getDatabaseName());
        assertEquals("app_user", config.getUsername());
    }
    
    @Test
    void testGetPropertyMethodWithDefinedProperty() {
        DatabaseConfig config = new DatabaseConfig();
        
        String host = config.getProperty("db.host");
        assertEquals("testhost", host);
    }
    
    @Test
    void testGetPropertyThrowsForUndefinedProperty() {
        DatabaseConfig config = new DatabaseConfig();
        
        assertThrows(DatabaseConfigurationException.class, 
            () -> config.getProperty("undefined.property"));
    }
    
    @Test
    void testGetPropertyWithFallbackForUndefinedProperty() {
        DatabaseConfig config = new DatabaseConfig();
        
        String value = config.getProperty("undefined.property", "fallback");
        assertEquals("fallback", value);
    }
    
    @Test
    void testInvalidPortThrowsInConstructor() {
        System.setProperty("DB_PORT", "99999");
        
        assertThrows(DatabaseConfigurationException.class, DatabaseConfig::new);
    }
    
    @Test
    void testInvalidHostThrowsInConstructor() {
        System.setProperty("DB_HOST", "invalid..host");
        
        assertThrows(DatabaseConfigurationException.class, DatabaseConfig::new);
    }
    
    @Test
    void testInvalidDatabaseNameThrowsInConstructor() {
        System.setProperty("APP_DB", "123invalid");
        
        assertThrows(DatabaseConfigurationException.class, DatabaseConfig::new);
    }
    
    @Test
    void testValidPortRange() {
        System.setProperty("DB_PORT", "1");
        assertDoesNotThrow(DatabaseConfig::new);
        
        System.setProperty("DB_PORT", "65535");
        assertDoesNotThrow(DatabaseConfig::new);
    }
    
    @Test
    void testConfigIsImmutableAfterConstruction() {
        DatabaseConfig config = new DatabaseConfig();
        String host1 = config.getHost();
        
        System.setProperty("DB_HOST", "different_host");
        
        String host2 = config.getHost();
        assertEquals(host1, host2);
    }
    
    @Test
    void testMultipleConfigInstancesAreIndependent() {
        DatabaseConfig config1 = new DatabaseConfig();
        
        System.setProperty("DB_HOST", "newhost");
        DatabaseConfig config2 = new DatabaseConfig();
        
        assertEquals("testhost", config1.getHost());
        assertEquals("newhost", config2.getHost());
    }
    
    @Test
    void testPasswordValidation() {
        System.setProperty("APP_PASSWORD", "ValidP@ss1");
        
        DatabaseConfig config = new DatabaseConfig();
        assertEquals("ValidP@ss1", config.getPassword());
    }

    @Test
    void testInvalidPasswordThrowsInConstructor() {
        System.setProperty("APP_PASSWORD", "weak");
        
        assertThrows(DatabaseConfigurationException.class, DatabaseConfig::new);
    }

    @Test
    void testBlankHostThrowsInConstructor() {
        System.setProperty("DB_HOST", "   ");
        
        assertThrows(DatabaseConfigurationException.class, DatabaseConfig::new);
    }

    @Test
    void testBlankPasswordThrowsInConstructor() {
        System.setProperty("APP_PASSWORD", "   ");
        
        assertThrows(DatabaseConfigurationException.class, DatabaseConfig::new);
    }
}
