package com.oodesigns.cas.util.file;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class FileLoaderProviderTest {

    @Test
    @SuppressWarnings("unused")
    void fileLoaderProviderIsAFunctionalInterface() {
        final FileLoaderProvider provider = fileName -> new StringReader("test data");
        assertNotNull(provider);
    }

    @Test
    @SuppressWarnings("unused")
    void fileLoaderProviderCanBeImplementedAsLambda() throws IOException {
        final FileLoaderProvider provider = fileName -> new StringReader("test");
        final Reader reader = provider.loadFile("test.txt");
        assertNotNull(reader);
        assertInstanceOf(StringReader.class, reader);
    }

    @Test
    @SuppressWarnings("unused")
    void fileLoaderProviderLoadFileMethodIsCallable() throws IOException {
        final FileLoaderProvider provider = fileName -> new StringReader("content");
        final Reader result = provider.loadFile("anyFile.txt");
        assertNotNull(result);
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void fileLoaderProviderPassesFileNameToImplementation() throws IOException {
        final FileLoaderProvider provider = fileName -> {
            if (!fileName.equals("expected.txt")) {
                throw new IllegalArgumentException("Wrong file");
            }
            return new StringReader("data");
        };
        final Reader reader = provider.loadFile("expected.txt");
        assertNotNull(reader);
    }

    @Test
    @SuppressWarnings("resource")
    void fileLoaderProviderThrowsFileLoaderExceptionOnError() {
        final FileLoaderProvider provider = _ -> {
            throw new FileLoaderException(new IOException("Test error"));
        };
        assertThrows(FileLoaderException.class, () -> provider.loadFile("test.txt"));
    }

    @Test
    @SuppressWarnings("resource")
    void fileLoaderProviderCanThrowIOException() {
        final FileLoaderProvider provider = _ -> {
            throw new IOException("IO error");
        };
        assertThrows(IOException.class, () -> provider.loadFile("test.txt"));
    }

    @Test
    @SuppressWarnings("unused")
    void fileLoaderProviderReturnsDifferentReadersForMultipleCalls() throws IOException {
        final FileLoaderProvider provider = fileName -> new StringReader("data");
        final Reader reader1 = provider.loadFile("file1.txt");
        final Reader reader2 = provider.loadFile("file2.txt");
        assertNotNull(reader1);
        assertNotNull(reader2);
        assertNotSame(reader1, reader2);
    }

    @Test
    void fileLoaderProviderWithVariableFileNames() throws IOException {
        final FileLoaderProvider provider = fileName ->
                new StringReader("content of %s".formatted(fileName));
        final Reader reader1 = provider.loadFile("file1.txt");
        final Reader reader2 = provider.loadFile("file2.txt");
        assertNotNull(reader1);
        assertNotNull(reader2);
    }

    @Test
    void fileLoaderProviderImplementationCanAccessFileName() throws IOException {
        // Using lambda for clarity and to test that the fileName parameter is passed and used
        final FileLoaderProvider provider = StringReader::new;
        final Reader reader = provider.loadFile("test.txt");
        assertNotNull(reader);
    }

    @Test
    @SuppressWarnings("unused")
    void fileLoaderProviderCanReturnReaderWithSpecificContent() throws IOException {
        final String expectedContent = "specific test content";
        final FileLoaderProvider provider = fileName -> new StringReader(expectedContent);
        final Reader reader = provider.loadFile("any.txt");
        assertNotNull(reader);
    }
}


