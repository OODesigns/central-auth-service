package com.oodesigns.cas.util.properties;

import java.io.IOException;
import java.util.Properties;
import java.util.function.UnaryOperator;
import com.oodesigns.cas.util.file.FileLoaderException;
import com.oodesigns.cas.util.file.FileLoaderProvider;

/**
 * Reads and parses properties files using FileLoader.
 * Applies transformations to property values using an injected transformer.
 */
public final class PropertiesReader {
    private final Properties properties;
    private final UnaryOperator<String> transformer;

    /**
     * Creates a PropertiesReader by loading and parsing the specified properties file.
     * The transformer is applied to all property values after loading.
     * Fails fast if the file cannot be found or parsed.
     *
     * @param fileName The name of the resource file to load
     * @param transformer The function to transform property values
     * @param fileLoaderProvider The provider for loading file content (allows dependency injection for testing)
     * @throws PropertiesReaderException if the file cannot be loaded or parsed
     */
    public PropertiesReader(final String fileName, final UnaryOperator<String> transformer, final FileLoaderProvider fileLoaderProvider) {
        this.transformer = transformer;
        this.properties = new Properties();
        try {
            this.properties.load(fileLoaderProvider.loadFile(fileName));
        } catch (final FileLoaderException e) {
            throw new PropertiesReaderException(String.format("Unable to find %s", fileName), e);
        } catch (final IOException e) {
            throw new PropertiesReaderException(String.format("Failed to parse %s", fileName), e);
        }
    }
    /**
     * Gets a property value by key.
     * The value is transformed when requested.
     *
     * @param key The property key
     * @return The transformed property value, or empty string if not found
     */
    public String get(final String key) {
        final String value = properties.getProperty(key, "");
        return transformer.apply(value);
    }
}
