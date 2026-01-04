package com.oodesigns.cas.util.file;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

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
    void closeThrowsIOExceptionIsHandled() throws IOException {
        ClassLoader mockLoader = mock(ClassLoader.class);
        InputStream mockStream = mock(InputStream.class);

        when(mockLoader.getResourceAsStream("file.txt")).thenReturn(mockStream);
        when(mockStream.readAllBytes()).thenReturn("ok".getBytes(StandardCharsets.UTF_8));
        doThrow(new IOException("close failed")).when(mockStream).close();

        FileLoaderException ex = assertThrows(
                FileLoaderException.class,
                () -> new FileLoader("file.txt", mockLoader)
        );

        assertNotNull(ex.getCause());
        assertEquals("close failed", ex.getCause().getMessage());
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

    @Test
    void ioExceptionDuringConstructionIsWrapped() throws IOException {
        ClassLoader mockLoader = mock(ClassLoader.class);
        InputStream mockStream = mock(InputStream.class);
        IOException originalException = new IOException("Stream read failed");

        when(mockLoader.getResourceAsStream("file.txt")).thenReturn(mockStream);
        when(mockStream.readAllBytes()).thenThrow(originalException);

        FileLoaderException exception = assertThrows(
            FileLoaderException.class,
            () -> new FileLoader("file.txt", mockLoader)
        );

        // Verify the original IOException is wrapped as the cause
        assertSame(originalException, exception.getCause());
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void fileLoaderHandlesIOExceptionWithMessage() throws IOException {
        ClassLoader mockLoader = mock(ClassLoader.class);
        InputStream mockStream = mock(InputStream.class);

        when(mockLoader.getResourceAsStream("data.txt")).thenReturn(mockStream);
        when(mockStream.readAllBytes()).thenThrow(new IOException("Permission denied"));

        FileLoaderException exception = assertThrows(
            FileLoaderException.class,
            () -> new FileLoader("data.txt", mockLoader)
        );

        assertNotNull(exception);
        assertNotNull(exception.getCause());
        assertEquals("Permission denied", exception.getCause().getMessage());
    }


}