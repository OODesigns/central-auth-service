package com.oodesigns.cas.util.file;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileLoaderTest {

    @Test
    void unableToLoadFileThrows(){
        assertThrows(FileLoaderException.class, ()->new FileLoader("NoFile.txt"));
    }

    @Test
    void loadAndReadText(){
        final FileLoader fileLoader = new FileLoader("testfile.txt");
        assertEquals("some test data", fileLoader.toString());
    }

    @Test
    void toReaderReturnsStringReader(){
        final FileLoader fileLoader = new FileLoader("testfile.txt");
        final StringReader reader = fileLoader.toReader();
        assertNotNull(reader);
    }

    @Test
    void ioExceptionDuringReadIsHandled() throws IOException {
        ClassLoader mockLoader = mock(ClassLoader.class);
        InputStream mockStream = mock(InputStream.class);

        when(mockLoader.getResourceAsStream("test.txt")).thenReturn(mockStream);
        when(mockStream.readAllBytes()).thenThrow(new IOException("Read error"));

        assertThrows(FileLoaderException.class, () -> new FileLoader("test.txt", mockLoader));

        verify(mockStream).close();
    }

    @Test
    void publicConstructorLoadsFileFromClasspath() {
        final FileLoader fileLoader = new FileLoader("testfile.txt");
        assertNotNull(fileLoader);
        assertEquals("some test data", fileLoader.toString());
    }

    @Test
    void publicConstructorWithNonExistentFileThrows() {
        assertThrows(FileLoaderException.class, () -> new FileLoader("nonexistent-file-xyz.txt"));
    }

    @Test
    void toReaderMultipleCallsReturnIndependentReaders() {
        final FileLoader fileLoader = new FileLoader("testfile.txt");
        final StringReader reader1 = fileLoader.toReader();
        final StringReader reader2 = fileLoader.toReader();
        
        assertNotNull(reader1);
        assertNotNull(reader2);
        assertNotSame(reader1, reader2);
    }

    @Test
    void fileLoaderWithNullResourceThrows() {
        ClassLoader mockLoader = mock(ClassLoader.class);
        when(mockLoader.getResourceAsStream("missing.txt")).thenReturn(null);

        assertThrows(FileLoaderException.class, () -> new FileLoader("missing.txt", mockLoader));
    }

    @Test
    void fileLoaderPreservesFileContent() {
        final FileLoader fileLoader = new FileLoader("testfile.txt");
        final String content1 = fileLoader.toString();
        final String content2 = fileLoader.toString();
        
        assertEquals(content1, content2);
    }
}