package com.oodesigns.cas.util.file;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class FileLoaderExceptionTest {
    @Test
    void exceptionWrapsTheGivenException() {
        IOException originalException = new IOException("Test IO error");
        FileLoaderException exception = new FileLoaderException(originalException);
        assertNotNull(exception);
        assertSame(originalException, exception.getCause());
    }
    @Test
    void exceptionIsRuntimeException() {
        IOException originalException = new IOException("Test error");
        FileLoaderException exception = new FileLoaderException(originalException);
        assertInstanceOf(RuntimeException.class, exception);
    }
    @Test
    void exceptionWithDifferentIOExceptionMessages() {
        IOException originalException = new IOException("File not found");
        FileLoaderException exception = new FileLoaderException(originalException);
        assertEquals("File not found", exception.getCause().getMessage());
    }
    @Test
    void exceptionCanBeThrownAndCaught() {
        IOException originalException = new IOException("Read error");
        assertThrows(FileLoaderException.class, () -> {
            throw new FileLoaderException(originalException);
        });
    }
    @Test
    void exceptionProvidesAccessToCause() {
        IOException originalException = new IOException("Stream closed");
        FileLoaderException exception = new FileLoaderException(originalException);
        Throwable cause = exception.getCause();
        assertNotNull(cause);
        assertInstanceOf(IOException.class, cause);
        assertEquals("Stream closed", cause.getMessage());
    }
    @Test
    void exceptionWithNullMessage() {
        IOException originalException = new IOException();
        FileLoaderException exception = new FileLoaderException(originalException);
        assertNotNull(exception.getCause());
        assertNull(exception.getCause().getMessage());
    }
    @Test
    void exceptionPreservesStackTrace() {
        IOException originalException = new IOException("Original error");
        FileLoaderException exception = new FileLoaderException(originalException);
        StackTraceElement[] stackTrace = exception.getStackTrace();
        assertNotNull(stackTrace);
        assertTrue(stackTrace.length > 0);
    }
}
