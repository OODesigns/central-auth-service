package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.KeyPassword;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Supplies clearable key material from Linux-local, workload-mounted secret files. */
public final class FileKeySupplier implements KeySupplier {

    private final Path secretDirectory;

    public FileKeySupplier(final Path secretDirectory) {
        this.secretDirectory = Objects.requireNonNull(secretDirectory, "Secret directory cannot be null")
            .toAbsolutePath().normalize();
    }

    @Override
    public Optional<KeyPassword> getPassword(final String keyId) {
        if (!isSafeKeyId(keyId)) {
            return Optional.empty();
        }
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(secretDirectory.resolve(keyId));
        } catch (final IOException exception) {
            return Optional.empty();
        }
        try {
            final char[] characters = stripTrailingLineBreak(new String(bytes, StandardCharsets.UTF_8)).toCharArray();
            try {
                return Optional.of(KeyPassword.of(characters));
            } catch (final IllegalArgumentException exception) {
                return Optional.empty();
            } finally {
                Arrays.fill(characters, '\0');
            }
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private boolean isSafeKeyId(final String keyId) {
        return keyId != null && keyId.matches("[A-Za-z0-9][A-Za-z0-9_.-]*");
    }

    private String stripTrailingLineBreak(final String value) {
        return value.endsWith("\r\n") ? value.substring(0, value.length() - 2)
            : value.endsWith("\n") ? value.substring(0, value.length() - 1)
            : value;
    }
}