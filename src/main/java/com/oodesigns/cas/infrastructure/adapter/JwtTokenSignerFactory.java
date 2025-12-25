package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.KeyPassword;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * Factory for creating JwtTokenSigner instances backed by on-demand key retrieval.
 * Secrets are materialised as Password objects and cleared immediately after use.
 */
public final class JwtTokenSignerFactory {

    private JwtTokenSignerFactory() {
        // Utility class
    }

    /**
     * Create a JwtTokenSigner that reads the secret from an environment variable when needed.
     *
     * @param envVarName Name of the environment variable containing the secret
    * @return JwtTokenSigner using on-demand password retrieval
     * @throws IllegalArgumentException if the environment variable is missing or too short
     */
    public static JwtTokenSigner fromEnvironment(final String envVarName) {
        Objects.requireNonNull(envVarName, "Environment variable name cannot be null");

        final String initialValue = System.getenv(envVarName);
        validateSecret(initialValue, "Environment variable '" + envVarName + "'");

        final KeySupplier supplier = () -> {
            final String current = System.getenv(envVarName);
            validateSecret(current, "Environment variable '" + envVarName + "'");
            final char[] chars = current.toCharArray();
            try {
                return KeyPassword.of(chars);
            } finally {
                Arrays.fill(chars, '\0');
            }
        };

        return new JwtTokenSigner(supplier);
    }

    /**
     * Create a JwtTokenSigner that reads the secret from a file when needed.
     *
     * @param filePath Path to the file containing the secret material
    * @return JwtTokenSigner using on-demand password retrieval
     * @throws IllegalArgumentException if the file cannot be read or content is invalid
     */
    public static JwtTokenSigner fromFile(final String filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        validateSecret(readSecret(filePath), "Secret file '" + filePath + "'");

        final KeySupplier supplier = () -> {
            final String secret = readSecret(filePath);
            validateSecret(secret, "Secret file '" + filePath + "'");
            final char[] chars = secret.toCharArray();
            try {
                return KeyPassword.of(chars);
            } finally {
                Arrays.fill(chars, '\0');
            }
        };

        return new JwtTokenSigner(supplier);
    }

    private static void validateSecret(final String secret, final String sourceDescription) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(sourceDescription + " is missing or empty");
        }
        if (secret.length() < 32) {
            throw new IllegalArgumentException(sourceDescription + " must be at least 32 characters");
        }
    }

    private static String readSecret(final String filePath) {
        try {
            return stripPemHeaders(Files.readString(Path.of(filePath))).trim();
        } catch (final java.io.IOException e) {
            throw new IllegalArgumentException("Failed to read secret from file: " + filePath, e);
        }
    }

    private static String stripPemHeaders(final String content) {
        return content
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");
    }
}
