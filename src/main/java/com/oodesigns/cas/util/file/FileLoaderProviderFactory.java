package com.oodesigns.cas.util.file;
/**
 * Factory for creating FileLoaderProvider instances.
 */
public final class FileLoaderProviderFactory {
    
    private FileLoaderProviderFactory() {
        // Prevent instantiation
    }
    
    /**
     * Creates the default FileLoaderProvider that uses FileLoader.
     * @return A FileLoaderProvider that loads files using FileLoader
     */
    public static FileLoaderProvider defaultProvider() {
        return fileName -> {
            final FileLoader loader = new FileLoader(fileName);
            return loader.toReader();
        };
    }
}
