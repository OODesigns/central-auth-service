package com.oodesigns.cas.util.properties;

/**
 * Exception thrown when properties file cannot be read or parsed.
 */
public final class PropertiesReaderException extends RuntimeException {

    /**
     * Creates an exception with a message.
     *
     * @param message The error message
     */
    public PropertiesReaderException(final String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and cause.
     *
     * @param message The error message
     * @param cause The underlying cause
     */
    public PropertiesReaderException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
