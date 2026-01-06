package com.oodesigns.cas.util.file;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class ClasspathFileLoader implements FileLoaderProvider {
    @Override
    public Reader loadFile(String fileName) throws FileLoaderException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
        if (inputStream == null) {
            throw new FileLoaderException("File not found in classpath: " + fileName);
        }
        return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }
}

