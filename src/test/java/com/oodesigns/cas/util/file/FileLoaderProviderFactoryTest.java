package com.oodesigns.cas.util.file;
import org.junit.jupiter.api.Test;
import java.io.Reader;
import java.lang.reflect.Constructor;
import static org.junit.jupiter.api.Assertions.*;

class FileLoaderProviderFactoryTest {
    @Test
    void defaultProviderReturnsNonNull() {
        FileLoaderProvider provider = FileLoaderProviderFactory.defaultProvider();
        assertNotNull(provider);
    }
    @Test
    void defaultProviderCanLoadFile() throws Exception {
        FileLoaderProvider provider = FileLoaderProviderFactory.defaultProvider();
        Reader reader = provider.loadFile("testfile.txt");
        assertNotNull(reader);
    }
    @Test
    void defaultProviderReturnsReader() throws Exception {
        FileLoaderProvider provider = FileLoaderProviderFactory.defaultProvider();
        Reader reader = provider.loadFile("testfile.txt");
        assertInstanceOf(java.io.StringReader.class, reader);
    }

    @Test
    @SuppressWarnings("resource")
    void defaultProviderThrowsOnMissingFile() {
        FileLoaderProvider provider = FileLoaderProviderFactory.defaultProvider();
        assertThrows(FileLoaderException.class, () -> provider.loadFile("nonexistent.txt"));
    }
    @Test
    void defaultProviderCanBeCalledMultipleTimes() throws Exception {
        FileLoaderProvider provider1 = FileLoaderProviderFactory.defaultProvider();
        FileLoaderProvider provider2 = FileLoaderProviderFactory.defaultProvider();
        assertNotNull(provider1);
        assertNotNull(provider2);
        Reader reader1 = provider1.loadFile("testfile.txt");
        Reader reader2 = provider2.loadFile("testfile.txt");
        assertNotNull(reader1);
        assertNotNull(reader2);
    }
    @Test
    @SuppressWarnings("resource")
    void defaultProviderThrowsFileLoaderExceptionForInvalidFile() {
        FileLoaderProvider provider = FileLoaderProviderFactory.defaultProvider();
        assertThrows(FileLoaderException.class, () -> provider.loadFile("invalid-file-xyz.txt"));
    }
    @Test
    void factoryHasPrivateConstructor() {
        Constructor<?>[] constructors = FileLoaderProviderFactory.class.getDeclaredConstructors();
        assertTrue(constructors.length >= 1);
        boolean hasPrivateConstructor = false;
        for (Constructor<?> constructor : constructors) {
            if (!java.lang.reflect.Modifier.isPublic(constructor.getModifiers())) {
                hasPrivateConstructor = true;
                break;
            }
        }
        assertTrue(hasPrivateConstructor);
    }
    @Test
    void defaultProviderFunctionalInterfaceImplementation() {
        FileLoaderProvider provider = FileLoaderProviderFactory.defaultProvider();
        assertInstanceOf(FileLoaderProvider.class, provider);
    }
    @Test
    void defaultProviderReturnsNewReadersEachCall() throws Exception {
        FileLoaderProvider provider = FileLoaderProviderFactory.defaultProvider();
        Reader reader1 = provider.loadFile("testfile.txt");
        Reader reader2 = provider.loadFile("testfile.txt");
        assertNotNull(reader1);
        assertNotNull(reader2);
        assertNotSame(reader1, reader2);
    }
}
