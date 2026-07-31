package com.oodesigns.cas.util.file;

import java.io.IOException;

/**
 * Interface for loading file content. Used for testability.
 */
@FunctionalInterface
public interface FileLoaderProvider {
    java.io.Reader loadFile(String fileName) throws FileLoaderException, IOException;
}
