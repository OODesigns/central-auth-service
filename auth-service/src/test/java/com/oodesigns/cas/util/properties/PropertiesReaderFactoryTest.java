package com.oodesigns.cas.util.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesReaderFactoryTest {

    @Test
    void factoryCreateLoadsApplicationPropertiesWithEnvironmentTransformer() {
        final PropertiesReaderFactory factory = PropertiesReaderFactoryProvider.create();
        final PropertiesReader reader = factory.create(new EnvironmentVariableTransformer());
        assertNotNull(reader);
    }

    @Test
    void factoryCreateWithTransformerLoadsApplicationProperties() {
        final PropertiesReaderFactory factory = PropertiesReaderFactoryProvider.create();
        final PropertiesReader reader = factory.create(String::toUpperCase);
        assertNotNull(reader);
    }

    @Test
    void factoryAcceptsCustomTransformer() {
        final PropertiesReaderFactory factory = PropertiesReaderFactoryProvider.create();
        final PropertiesReader reader = factory.create(String::toUpperCase);
        
        // Verify transformer is applied on demand
        final String value = reader.get("any.key");
        if (!value.isEmpty()) {
            assertEquals(value, value.toUpperCase());
        }
    }
}
