package com.oodesigns.cas.util.properties;

import java.util.function.UnaryOperator;

/**
 * Factory for creating PropertiesReader instances.
 * Provides convenient methods for loading application.properties with optional transformers.
 */
public final class PropertiesReaderFactory {

    private static final String APPLICATION_PROPERTIES = "application.properties";

    private PropertiesReaderFactory() {
        // Factory class - instantiation not permitted
    }

    /**
     * Creates a PropertiesReader with environment variable transformation.
     * Uses EnvironmentVariableTransformer by default.
     *
     * @return A PropertiesReader with environment variable resolution
     * @throws PropertiesReaderException if the file cannot be loaded or parsed
     */
    public static PropertiesReader create() {
        return create(new EnvironmentVariableTransformer());
    }

    /**
     * Creates a PropertiesReader with the specified transformer.
     *
     * @param transformer The function to transform property values
     * @return A PropertiesReader with the loaded and transformed properties
     * @throws PropertiesReaderException if the file cannot be loaded or parsed
     */
    public static PropertiesReader create(final UnaryOperator<String> transformer) {
        return new PropertiesReader(APPLICATION_PROPERTIES, transformer);
    }
}
