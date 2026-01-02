package com.oodesigns.cas.util.file;

import org.junit.jupiter.api.Test;
import java.io.StringReader;
import static org.junit.jupiter.api.Assertions.*;

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
}