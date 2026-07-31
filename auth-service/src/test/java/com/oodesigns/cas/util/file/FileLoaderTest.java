package com.oodesigns.cas.util.file;

import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
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
    void loadAndReadTextStream() {
        final FileLoader fileLoader = new FileLoader("testfile.txt");
        final String content = new BufferedReader(fileLoader.toReader())
                .lines()
                .reduce("", String::concat);
        assertEquals("some test data", content);
    }

}