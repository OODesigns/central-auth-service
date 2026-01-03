package com.oodesigns.cas.util.properties;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.oodesigns.cas.util.file.FileLoaderProvider;
/**
 * Factory for creating PropertiesReader instances.
 * Provides convenient methods for loading application.properties with optional transformers.
 */
public class PropertiesReaderFactory {

    private static final String APPLICATION_PROPERTIES = "application.properties";
    private Supplier<FileLoaderProvider> fileLoaderProviderSupplier; 

    PropertiesReaderFactory(Supplier<FileLoaderProvider> fileLoaderProviderSupplier) {
        this.fileLoaderProviderSupplier = fileLoaderProviderSupplier;
    }

    /**
     * Creates a PropertiesReader with the specified transformer.
     *
     * @param transformer The function to transform property values
     * @return A PropertiesReader with the loaded and transformed properties
     * @throws PropertiesReaderException if the file cannot be loaded or parsed
     */
    public PropertiesReader create(final UnaryOperator<String> transformer) {
        return new PropertiesReader(APPLICATION_PROPERTIES, transformer, this.fileLoaderProviderSupplier.get());
    }
}
