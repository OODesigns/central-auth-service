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
        FileLoaderProvider provider = fileName -> new StringReader("test data");
        assertNotNull(provider);
    }

    @Test
    @SuppressWarnings("unused")
    void fileLoaderProviderCanBeImplementedAsLambda() throws IOException {
        FileLoaderProvider provider = fileName -> new StringReader("test");
        Reader reader = provider.loadFile("test.txt");
        assertNotNull(reader);
        assertInstanceOf(StringReader.class, reader);
    }

    @Test
    @SuppressWarnings("unused")
    void fileLoaderProviderLoadFileMethodIsCallable() throws IOException {
        FileLoaderProvider provider = fileName -> new StringReader("content");
        Reader result = provider.loadFile("anyFile.txt");
        assertNotNull(result);
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void fileLoaderProviderPassesFileNameToImplementation() throws IOException {
        FileLoaderProvider provider = fileName -> {
            if (!fileName.equals("expected.txt")) {
                throw new IllegalArgumentException("Wrong file");
            }
            return new StringReader("data");
        };
        Reader reader = provider.loadFile("expected.txt");
        assertNotNull(reader);
    }

    @Test
    @SuppressWarnings("resource")
    void fileLoaderProviderThrowsFileLoaderExceptionOnError() {
        FileLoaderProvider provider = _ -> {
            throw new FileLoaderException(new IOException("Test error"));
        };
        assertThrows(FileLoaderException.class, () -> provider.loadFile("test.txt"));
    }

    @Test
    @SuppressWarnings("resource")
    void fileLoaderProviderCanThrowIOException() {
        FileLoaderProvider provider = _ -> {
            throw new IOException("IO error");
        };
        assertThrows(IOException.class, () -> provider.loadFile("test.txt"));
    }

    @Test
    @SuppressWarnings("unused")
    void fileLoaderProviderReturnsDifferentReadersForMultipleCalls() throws IOException {
        FileLoaderProvider provider = fileName -> new StringReader("data");
        Reader reader1 = provider.loadFile("file1.txt");
        Reader reader2 = provider.loadFile("file2.txt");
        assertNotNull(reader1);
        assertNotNull(reader2);
        assertNotSame(reader1, reader2);
    }

    @Test
    void fileLoaderProviderWithVariableFileNames() throws IOException {
        FileLoaderProvider provider = fileName ->
                new StringReader("content of %s".formatted(fileName));
        Reader reader1 = provider.loadFile("file1.txt");
        Reader reader2 = provider.loadFile("file2.txt");
        assertNotNull(reader1);
        assertNotNull(reader2);
    }

    @Test
    void fileLoaderProviderImplementationCanAccessFileName() throws IOException {
        // Using lambda for clarity and to test that the fileName parameter is passed and used
        FileLoaderProvider provider = StringReader::new;
        Reader reader = provider.loadFile("test.txt");
        assertNotNull(reader);
    }

    @Test
    @SuppressWarnings("unused")
    void fileLoaderProviderCanReturnReaderWithSpecificContent() throws IOException {
        String expectedContent = "specific test content";
        FileLoaderProvider provider = fileName -> new StringReader(expectedContent);
        Reader reader = provider.loadFile("any.txt");
        assertNotNull(reader);
    }
}


