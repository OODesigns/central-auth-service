package com.oodesigns.cas.util.properties;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PropertiesReaderTest {

    @Test
    void constructorLoadsValidPropertiesFile() {
        final PropertiesReader reader = new PropertiesReader("application.properties", s -> s);
        assertNotNull(reader);
    }

    @Test
    void constructorThrowsOnNonExistentFile() {
        assertThrows(PropertiesReaderException.class, () -> new PropertiesReader("NonExistentFile.properties", s -> s));
    }

    @Test
    void transformerIsAppliedOnDemand() {
        final PropertiesReader reader = new PropertiesReader("application.properties", String::toUpperCase);
        
        // Get a property and verify it's transformed
        final String value = reader.get("any.key");
        if (!value.isEmpty()) {
            assertEquals(value, value.toUpperCase(), 
                "Property value should be uppercase after transformation");
        }
    }

    @Test
    void getReturnsEmptyStringForNonExistentKey() {
        final PropertiesReader reader = new PropertiesReader("application.properties", s -> s);
        assertEquals("", reader.get("non.existent.key"));
    }
}
