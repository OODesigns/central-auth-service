package com.oodesigns.cas.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DatabaseConnectionException")
class DatabaseConnectionExceptionTest {

    @Test
    @DisplayName("constructor with message sets message correctly")
    void testConstructorWithMessage() {
        final String message = "Connection failed";
        
        final DatabaseConnectionException exception = new DatabaseConnectionException(message);
        
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("constructor with message and cause sets both correctly")
    void testConstructorWithMessageAndCause() {
        final String message = "Connection failed";
        final Throwable cause = new RuntimeException("Network error");
        
        final DatabaseConnectionException exception = new DatabaseConnectionException(message, cause);
        
        assertEquals(message, exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    @DisplayName("exception is a RuntimeException")
    void testIsRuntimeException() {
        final DatabaseConnectionException exception = new DatabaseConnectionException("test");
        
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    @DisplayName("exception can be thrown and caught")
    void testCanBeThrown() {
        assertThrows(DatabaseConnectionException.class, () -> {
            throw new DatabaseConnectionException("test error");
        });
    }
}
