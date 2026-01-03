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
}