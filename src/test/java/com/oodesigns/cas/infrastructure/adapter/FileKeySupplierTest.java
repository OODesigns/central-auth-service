package com.oodesigns.cas.infrastructure.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileKeySupplierTest {

    @TempDir
    Path secretDirectory;

    @Test
    void readsMountedSecretFileAndRemovesTrailingLineBreak() throws Exception {
        Files.writeString(secretDirectory.resolve("JWT_SECRET"), "0123456789ABCDEF0123456789ABCDEF\n");

        assertTrue(new FileKeySupplier(secretDirectory).getPassword("JWT_SECRET").isPresent());
    }

    @Test
    void removesWindowsTrailingLineBreak() throws Exception {
        Files.writeString(secretDirectory.resolve("JWT_SECRET"), "0123456789ABCDEF0123456789ABCDEF\r\n");

        assertTrue(new FileKeySupplier(secretDirectory).getPassword("JWT_SECRET").isPresent());
    }

    @Test
    void rejectsInvalidSecretMaterial() throws Exception {
        Files.writeString(secretDirectory.resolve("JWT_SECRET"), "short");

        assertTrue(new FileKeySupplier(secretDirectory).getPassword("JWT_SECRET").isEmpty());
    }

    @Test
    void rejectsUnsafeOrMissingSecretNames() {
        final FileKeySupplier supplier = new FileKeySupplier(secretDirectory);

        assertTrue(supplier.getPassword("../JWT_SECRET").isEmpty());
        assertTrue(supplier.getPassword("missing").isEmpty());
    }

    @Test
    void implementsKeySupplierPort() {
        assertInstanceOf(KeySupplier.class, new FileKeySupplier(secretDirectory));
    }
}