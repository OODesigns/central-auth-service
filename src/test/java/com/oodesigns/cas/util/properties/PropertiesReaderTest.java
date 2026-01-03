package com.oodesigns.cas.util.properties;

import org.junit.jupiter.api.Test;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.oodesigns.cas.util.file.FileLoaderProvider;
import com.oodesigns.cas.util.file.FileLoaderProviderFactory;
import static org.junit.jupiter.api.Assertions.*;

class PropertiesReaderTest {

    @Test
    void constructorLoadsValidPropertiesFile() {
        final PropertiesReader reader = new PropertiesReader("application.properties", s -> s, FileLoaderProviderFactory.defaultProvider());
        assertNotNull(reader);
    }

    @Test
    void constructorThrowsOnNonExistentFile() {
        assertThrows(PropertiesReaderException.class, this::createNonExistentReader);
    }

    @Test
    void transformerIsAppliedOnDemand() {
        final PropertiesReader reader = new PropertiesReader("application.properties", String::toUpperCase, FileLoaderProviderFactory.defaultProvider());
        final String value = reader.get("any.key");
        if (!value.isEmpty()) {
            assertEquals(value, value.toUpperCase(),
                "Property value should be uppercase after transformation");
        }
    }

    @Test
    void getReturnsEmptyStringForNonExistentKey() {
        final PropertiesReader reader = new PropertiesReader("application.properties", s -> s, FileLoaderProviderFactory.defaultProvider());
        assertEquals("", reader.get("non.existent.key"));
    }

    @Test
    void constructorLogsWhenInfoLevelEnabled() {
        final Logger logger = Logger.getLogger(PropertiesReader.class.getName());
        final Level originalLevel = logger.getLevel();
        try {
            logger.setLevel(Level.INFO);
            final PropertiesReader reader = new PropertiesReader("application.properties", s -> s, FileLoaderProviderFactory.defaultProvider());
            assertNotNull(reader);
        } finally {
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void constructorSkipsLoggingWhenInfoLevelDisabled() {
        final Logger logger = Logger.getLogger(PropertiesReader.class.getName());
        final Level originalLevel = logger.getLevel();
        try {
            logger.setLevel(Level.WARNING);
            final PropertiesReader reader = new PropertiesReader("application.properties", s -> s, FileLoaderProviderFactory.defaultProvider());
            assertNotNull(reader);
        } finally {
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void constructorThrowsPropertiesReaderExceptionWithCorrectMessage() {
        final PropertiesReaderException exception = assertThrows(PropertiesReaderException.class, this::createMissingPropertiesReader);
        assert exception.getMessage().contains("Unable to find");
        assert exception.getMessage().contains("missing.properties");
    }

    @Test
    void getAppliesTransformerToExistingProperty() {
        final PropertiesReader reader = new PropertiesReader("application.properties",
            value -> value.isEmpty() ? "transformed" : value + "_suffix", FileLoaderProviderFactory.defaultProvider());
        final String result = reader.get("nonexistent");
        assertEquals("transformed", result);
    }

    @Test
    void constructorThrowsOnIOException() {
        FileLoaderProvider failingProvider = fileName -> {
            throw new java.io.IOException("Simulated IO failure");
        };
        PropertiesReaderException ex = assertThrows(PropertiesReaderException.class, () -> new PropertiesReader("application.properties", s -> s, failingProvider));
        assert ex.getMessage().contains("Failed to parse");
        assert ex.getCause() instanceof java.io.IOException;
    }

    private void createNonExistentReader() {
        new PropertiesReader("NonExistentFile.properties", s -> s, FileLoaderProviderFactory.defaultProvider());
    }

    private void createMissingPropertiesReader() {
        new PropertiesReader("missing.properties", s -> s, FileLoaderProviderFactory.defaultProvider());
    }
}
