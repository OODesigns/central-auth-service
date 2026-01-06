package com.oodesigns.cas.infrastructure.config;

/**
 * Exception thrown when database connection operations fail at runtime.
 * This includes connection validation failures and network issues,
 * distinct from configuration errors which occur at construction time.
 */
public class DatabaseConnectionException extends RuntimeException {
    
    /**
     * Constructs a new DatabaseConnectionException with the specified detail message.
     *
     * @param message the detail message
     */
    public DatabaseConnectionException(final String message) {
        super(message);
    }
    
    /**
     * Constructs a new DatabaseConnectionException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public DatabaseConnectionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
