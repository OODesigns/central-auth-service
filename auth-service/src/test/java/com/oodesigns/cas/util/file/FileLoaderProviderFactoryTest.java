package com.oodesigns.cas.util.file;
import org.junit.jupiter.api.Test;
import java.io.Reader;
import java.lang.reflect.Constructor;
import static org.junit.jupiter.api.Assertions.*;

class FileLoaderProviderFactoryTest {
    @Test
    void defaultProviderReturnsNonNull() {
        final FileLoaderProvider provider = FileLoaderProviderFactory.defaultProvider();
        assertNotNull(provider);
    }
    @Test
    void defaultProviderCanLoadFile() throws Exception {
        final FileLoaderProvider provider = FileLoaderProviderFactory.defaultProvider();
        try (final Reader reader = provider.loadFile("testfile.txt")) {
            assertNotNull(reader);
        }
    }
    @Test
    void defaultProviderReturnsReader() throws Exception {
        final FileLoaderProvider provider = FileLoaderProviderFactory.defaultProvider();
        try (final Reader reader = provider.loadFile("testfile.txt")) {
            assertInstanceOf(java.io.StringReader.class, reader);
        }
    }

    @Test
    void defaultProviderThrowsOnMissingFile() {
        final FileLoaderProvider provider = FileLoaderProviderFactory.defaultProvider();
        assertThrows(FileLoaderException.class, () -> {
            try (final var _ = provider.loadFile("nonexistent.txt")) {
                fail("Should have thrown FileLoaderException");
            }
        });
    }
    @Test
    void defaultProviderCanBeCalledMultipleTimes() throws Exception {
        final FileLoaderProvider provider1 = FileLoaderProviderFactory.defaultProvider();
        final FileLoaderProvider provider2 = FileLoaderProviderFactory.defaultProvider();
        assertNotNull(provider1);
        assertNotNull(provider2);
        try (final Reader reader1 = provider1.loadFile("testfile.txt");
             final Reader reader2 = provider2.loadFile("testfile.txt")) {
            assertNotNull(reader1);
            assertNotNull(reader2);
        }
    }
    @Test
    void defaultProviderThrowsFileLoaderExceptionForInvalidFile() {
        final FileLoaderProvider provider = FileLoaderProviderFactory.defaultProvider();
        assertThrows(FileLoaderException.class, () -> {
            try (final var _ = provider.loadFile("invalid-file-xyz.txt")) {
                fail("Should have thrown FileLoaderException");
            }
        });
    }
    @Test
    void factoryHasPrivateConstructor() {
        final Constructor<?>[] constructors = FileLoaderProviderFactory.class.getDeclaredConstructors();
        assertTrue(constructors.length >= 1);
        boolean hasPrivateConstructor = false;
        for (final Constructor<?> constructor : constructors) {
            if (!java.lang.reflect.Modifier.isPublic(constructor.getModifiers())) {
                hasPrivateConstructor = true;
                break;
            }
        }
        assertTrue(hasPrivateConstructor);
    }
    @Test
    void defaultProviderFunctionalInterfaceImplementation() {
        final FileLoaderProvider provider = FileLoaderProviderFactory.defaultProvider();
        assertInstanceOf(FileLoaderProvider.class, provider);
    }
    @Test
    void defaultProviderReturnsNewReadersEachCall() throws Exception {
        final FileLoaderProvider provider = FileLoaderProviderFactory.defaultProvider();
        try (final Reader reader1 = provider.loadFile("testfile.txt");
             final Reader reader2 = provider.loadFile("testfile.txt")) {
            assertNotNull(reader1);
            assertNotNull(reader2);
            assertNotSame(reader1, reader2);
        }
    }
}
