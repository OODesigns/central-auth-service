package com.oodesigns.cas.util.file;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;


public class FileLoader{

    private static final String RESOURCE_S_NOT_FOUND = "Resource %s not found";
    private final String data;

    public FileLoader(final String fileName) {
        this(fileName, FileLoader.class.getClassLoader());
    }

    FileLoader(final String fileName, final ClassLoader classLoader) {
        try (final InputStream inputStream = classLoader.getResourceAsStream(fileName)) {
            if (inputStream == null)
                throw new IOException(String.format(RESOURCE_S_NOT_FOUND, fileName));

            data = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        } catch (final IOException e) {
            throw new FileLoaderException(e);
        }
    }

    @Override
    public String toString() {
        return data;
    }

    public StringReader toReader() {
        return new StringReader(data);
    }
}
