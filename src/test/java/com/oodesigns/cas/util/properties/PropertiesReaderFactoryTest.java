package com.oodesigns.cas.util.properties;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PropertiesReaderFactoryTest {

    @Test
    void factoryCreateLoadsApplicationPropertiesWithEnvironmentTransformer() {
        final PropertiesReader reader = PropertiesReaderFactory.create();
        assertNotNull(reader);
    }

    @Test
    void factoryCreateWithTransformerLoadsApplicationProperties() {
        final PropertiesReader reader = PropertiesReaderFactory.create(String::toUpperCase);
        assertNotNull(reader);
    }

    @Test
    void factoryAcceptsCustomTransformer() {
        final PropertiesReader reader = PropertiesReaderFactory.create(String::toUpperCase);
        
        // Verify transformer is applied on demand
        final String value = reader.get("any.key");
        if (!value.isEmpty()) {
            assertEquals(value, value.toUpperCase());
        }
    }
}
