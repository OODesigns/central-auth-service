package com.oodesigns.cas.file;

import java.util.logging.Level;
import java.util.logging.Logger;

public class FileLoaderException extends RuntimeException {
    private static final Logger logger = Logger.getLogger(FileLoaderException.class.getName());

    public FileLoaderException(final Exception e) {
        super(e);
        logger.log(Level.SEVERE, e.getMessage());
    }
}
