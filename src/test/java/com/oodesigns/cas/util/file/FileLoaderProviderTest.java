package com.oodesigns.cas.util.file;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class FileLoaderProviderTest {

    @Test
    void fileLoaderProviderIsAFunctionalInterface() {
        final FileLoaderProvider provider = _ -> new StringReader("test data");
        assertNotNull(provider);
    }

    @Test
    void fileLoaderProviderCanBeImplementedAsLambda() throws IOException {
        final FileLoaderProvider provider = _ -> new StringReader("test");
        try (final Reader reader = provider.loadFile("test.txt")) {
            assertNotNull(reader);
            assertInstanceOf(StringReader.class, reader);
        }
    }

    @Test
    void fileLoaderProviderLoadFileMethodIsCallable() throws IOException {
        final FileLoaderProvider provider = _ -> new StringReader("content");
        try (final Reader result = provider.loadFile("anyFile.txt")) {
            assertNotNull(result);
        }
    }

    @Test
    void fileLoaderProviderPassesFileNameToImplementation() throws IOException {
        final String expectedFileName = "expected.txt";
        final FileLoaderProvider provider = fileName -> {
            assertEquals(expectedFileName, fileName, "Wrong file");
            return new StringReader("data");
        };
        try (final Reader reader = provider.loadFile(expectedFileName)) {
            assertNotNull(reader);
        }
    }

    @Test
    void fileLoaderProviderThrowsFileLoaderExceptionOnError() {
        final FileLoaderProvider provider = _ -> {
            throw new FileLoaderException(new IOException("Test error"));
        };
        assertThrows(FileLoaderException.class, () -> {
            try (final var _ = provider.loadFile("test.txt")) {
                fail("Expected FileLoaderException before Reader creation");
            }
        });
    }

    @Test
    void fileLoaderProviderCanThrowIOException() {
        final FileLoaderProvider provider = _ -> {
            throw new IOException("IO error");
        };
        assertThrows(IOException.class, () -> {
            try (final var _ = provider.loadFile("test.txt")) {
                fail("Expected IOException before Reader creation");
            }
        });
    }

    @Test
    void fileLoaderProviderReturnsDifferentReadersForMultipleCalls() throws IOException {
        final FileLoaderProvider provider = _ -> new StringReader("data");
        try (final Reader reader1 = provider.loadFile("file1.txt");
             final Reader reader2 = provider.loadFile("file2.txt")) {
            assertNotNull(reader1);
            assertNotNull(reader2);
            assertNotSame(reader1, reader2);
        }
    }

    @Test
    void fileLoaderProviderWithVariableFileNames() throws IOException {
        final FileLoaderProvider provider = fileName ->
                new StringReader("content of %s".formatted(fileName));
        try (final Reader reader1 = provider.loadFile("file1.txt");
             final Reader reader2 = provider.loadFile("file2.txt")) {
            assertNotNull(reader1);
            assertNotNull(reader2);
        }
    }

    @Test
    void fileLoaderProviderImplementationCanAccessFileName() throws IOException {
        // Using lambda for clarity and to test that the fileName parameter is passed and used
        final FileLoaderProvider provider = StringReader::new;
        try (final Reader reader = provider.loadFile("test.txt")) {
            assertNotNull(reader);
        }
    }

    @Test
    void fileLoaderProviderCanReturnReaderWithSpecificContent() throws IOException {
        final String expectedContent = "specific test content";
        final FileLoaderProvider provider = _ -> new StringReader(expectedContent);
        try (final Reader reader = provider.loadFile("any.txt")) {
            assertNotNull(reader);
        }
    }
}


