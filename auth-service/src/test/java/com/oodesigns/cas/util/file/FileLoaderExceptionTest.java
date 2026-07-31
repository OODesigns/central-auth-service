package com.oodesigns.cas.util.file;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class FileLoaderExceptionTest {
    @Test
    void exceptionWrapsTheGivenException() {
        final IOException originalException = new IOException("Test IO error");
        final FileLoaderException exception = new FileLoaderException(originalException);
        assertNotNull(exception);
        assertSame(originalException, exception.getCause());
    }
    @Test
    void exceptionIsRuntimeException() {
        final IOException originalException = new IOException("Test error");
        final FileLoaderException exception = new FileLoaderException(originalException);
        assertInstanceOf(RuntimeException.class, exception);
    }
    @Test
    void exceptionWithDifferentIOExceptionMessages() {
        final IOException originalException = new IOException("File not found");
        final FileLoaderException exception = new FileLoaderException(originalException);
        assertEquals("File not found", exception.getCause().getMessage());
    }
    @Test
    void exceptionCanBeThrownAndCaught() {
        final IOException originalException = new IOException("Read error");
        assertThrows(FileLoaderException.class, () -> {
            throw new FileLoaderException(originalException);
        });
    }
    @Test
    void exceptionProvidesAccessToCause() {
        final IOException originalException = new IOException("Stream closed");
        final FileLoaderException exception = new FileLoaderException(originalException);
        final Throwable cause = exception.getCause();
        assertNotNull(cause);
        assertInstanceOf(IOException.class, cause);
        assertEquals("Stream closed", cause.getMessage());
    }
    @Test
    void exceptionWithNullMessage() {
        final IOException originalException = new IOException();
        final FileLoaderException exception = new FileLoaderException(originalException);
        assertNotNull(exception.getCause());
        assertNull(exception.getCause().getMessage());
    }
    @Test
    void exceptionPreservesStackTrace() {
        final IOException originalException = new IOException("Original error");
        final FileLoaderException exception = new FileLoaderException(originalException);
        final StackTraceElement[] stackTrace = exception.getStackTrace();
        assertNotNull(stackTrace);
        assertTrue(stackTrace.length > 0);
    }
}
