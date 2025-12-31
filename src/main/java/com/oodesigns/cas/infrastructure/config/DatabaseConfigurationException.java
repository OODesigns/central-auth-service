package com.oodesigns.cas.infrastructure.config;

/**
 * Exception thrown when database configuration fails.
 * This includes issues with loading properties, invalid configuration values,
 * or failures in establishing database connections.
 */
public class DatabaseConfigurationException extends RuntimeException {
    
    /**
     * Constructs a new DatabaseConfigurationException with the specified detail message.
     *
     * @param message the detail message
     */
    public DatabaseConfigurationException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new DatabaseConfigurationException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public DatabaseConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
