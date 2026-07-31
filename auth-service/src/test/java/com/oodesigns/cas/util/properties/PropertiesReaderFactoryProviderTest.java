package com.oodesigns.cas.util.properties;

import org.junit.jupiter.api.Test;
import java.util.function.Supplier;

import com.oodesigns.cas.util.file.FileLoaderProvider;
import static org.junit.jupiter.api.Assertions.*;

class PropertiesReaderFactoryProviderTest {

    @Test
    void createWithDefaultFileLoaderProvider() {
        final PropertiesReaderFactory factory = PropertiesReaderFactoryProvider.create();
        assertNotNull(factory);
    }

    @Test
    void createWithCustomFileLoaderProviderSupplier() {
        final Supplier<FileLoaderProvider> customSupplier = () -> 
            fileName -> new java.io.StringReader("");
        final PropertiesReaderFactory factory = PropertiesReaderFactoryProvider.create(customSupplier);
        assertNotNull(factory);
    }

    @Test
    void factoryCreatesPropertiesReaderWithTransformer() {
        final PropertiesReaderFactory factory = PropertiesReaderFactoryProvider.create();
        final PropertiesReader reader = factory.create(String::toUpperCase);
        assertNotNull(reader);
    }
}
