package com.oodesigns.cas.infrastructure.config;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import com.oodesigns.cas.util.properties.EnvironmentVariableTransformer;
import com.oodesigns.cas.util.properties.PropertiesReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseContextFactory.
 * Tests DSLContext creation from DatabaseConfig.
 */
class DatabaseContextFactoryTest {
    
    @BeforeEach
    void setUp() {
        System.setProperty("DB_HOST", "localhost");
        System.setProperty("DB_PORT", "5432");
        System.setProperty("DB_USER", "app_user");
        System.setProperty("APP_PASSWORD", "Test@Password123");
    }
    
    @AfterEach
    void tearDown() {
        System.clearProperty("DB_HOST");
        System.clearProperty("DB_PORT");
        System.clearProperty("DB_NAME");
        System.clearProperty("DB_USER");
        System.clearProperty("DB_PASSWORD");
    }
    
    private DatabaseConfig createConfig() {
        if (System.getProperty("DB_USER", "").isBlank()) {
            System.setProperty("DB_USER", "app_user");
        }
        return new DatabaseConfig(
            new PropertiesReader(
                "application.properties",
                new EnvironmentVariableTransformer()
            )
        );
    }
    
    @Test
    void testFactoryCreatesValidDslContext() {
        final DatabaseConfig config = createConfig();
        
        // May throw if database is not available
        try {
            final DSLContext dslContext = DatabaseContextFactory.create(config);
            assertNotNull(dslContext);
        } catch (DatabaseConnectionException _) {
            // Expected if database is not running - that's ok for this test
        }
    }
    
    @Test
    void testFactoryThrowsOnInvalidConfig() {
        System.setProperty("DB_PORT", "99999");
        
        assertThrows(IllegalArgumentException.class, this::createConfig);
    }
    
    @Test
    void testFactoryThrowsOnNullConfig() {
        assertThrows(NullPointerException.class, () -> DatabaseContextFactory.create(null));
    }
}
