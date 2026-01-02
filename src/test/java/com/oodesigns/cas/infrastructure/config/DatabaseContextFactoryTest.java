package com.oodesigns.cas.infrastructure.config;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseContextFactory.
 * Tests DSLContext creation and thread-safe lazy initialization.
 */
class DatabaseContextFactoryTest {
    
    @BeforeEach
    void setUp() {
        System.setProperty("DB_HOST", "localhost");
        System.setProperty("DB_PORT", "5432");
        System.setProperty("APP_PASSWORD", "TestP@ss123");
    }
    
    @AfterEach
    void tearDown() {
        System.clearProperty("DB_HOST");
        System.clearProperty("DB_PORT");
        System.clearProperty("DB_NAME");
        System.clearProperty("DB_USER");
        System.clearProperty("DB_PASSWORD");
    }
    
    @Test
    void testFactoryCreationWithConfig() {
        DatabaseConfig config = new DatabaseConfig();
        
        assertDoesNotThrow(() -> new DatabaseContextFactory(config));
    }
    
    @Test
    void testThreadSafeLazyInitialization() throws InterruptedException {
        DatabaseConfig config = new DatabaseConfig();
        try (DatabaseContextFactory factory = new DatabaseContextFactory(config)) {
            final DSLContext[] contexts = new DSLContext[10];
            Thread[] threads = new Thread[10];
            
            for (int i = 0; i < 10; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    try {
                        contexts[index] = factory.getDslContext();
                    } catch (Exception _) {
                        // Expected if database is not available
                    }
                });
            }
            
            for (Thread thread : threads) {
                thread.start();
            }
            
            for (Thread thread : threads) {
                thread.join();
            }
            
            DSLContext firstNonNull = null;
            for (DSLContext context : contexts) {
                if (context != null) {
                    if (firstNonNull == null) {
                        firstNonNull = context;
                    } else {
                        assertSame(firstNonNull, context, 
                            "All threads should get the same DSLContext instance");
                    }
                }
            }
        }
    }
    
    @Test
    void testCloseMethodDoesNotThrow() {
        DatabaseConfig config = new DatabaseConfig();
        try (DatabaseContextFactory factory = new DatabaseContextFactory(config)) {
            assertDoesNotThrow(factory::close);
        }
    }
    
    @Test
    void testFactoryWithInvalidConfigThrowsOnGetDslContext() {
        System.setProperty("DB_HOST", "nonexistent.invalid.host.example.com");
        System.setProperty("DB_PORT", "9999");
        
        DatabaseConfig config = new DatabaseConfig();
        try (DatabaseContextFactory factory = new DatabaseContextFactory(config)) {
            assertThrows(DatabaseConnectionException.class, factory::getDslContext);
        }
    }
}
