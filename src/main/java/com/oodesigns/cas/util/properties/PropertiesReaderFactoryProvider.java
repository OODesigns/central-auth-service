package com.oodesigns.cas.util.properties;

import java.util.function.Supplier;

import com.oodesigns.cas.util.file.FileLoaderProvider;
import com.oodesigns.cas.util.file.FileLoaderProviderFactory;

/**
 * Provider for creating PropertiesReaderFactory instances.
 */
public final class PropertiesReaderFactoryProvider {
    
    private PropertiesReaderFactoryProvider() {
        // Prevent instantiation
    }
    
    /**
     * Creates a PropertiesReaderFactory with default FileLoaderProvider.
     * @return A PropertiesReaderFactory instance
     */
    public static PropertiesReaderFactory create() {
        return new PropertiesReaderFactory(FileLoaderProviderFactory::defaultProvider);
    }
    
    /**
     * Creates a PropertiesReaderFactory with the specified FileLoaderProvider supplier.
     * @param fileLoaderProviderSupplier The supplier for FileLoaderProvider
     * @return A PropertiesReaderFactory instance
     */
    public static PropertiesReaderFactory create(Supplier<FileLoaderProvider> fileLoaderProviderSupplier) {
        return new PropertiesReaderFactory(fileLoaderProviderSupplier);
    }
}
