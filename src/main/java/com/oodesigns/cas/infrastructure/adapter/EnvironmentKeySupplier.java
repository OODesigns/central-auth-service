package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.KeyPassword;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * KeySupplier implementation that reads secrets from environment variables.
 * Retrieves the secret on-demand and converts to KeyPassword.
 * Uses the keyId parameter as the environment variable name.
 */
public final class EnvironmentKeySupplier implements KeySupplier {

    private static final Logger LOGGER = Logger.getLogger(EnvironmentKeySupplier.class.getName());
    private final UnaryOperator<String> envReader;

    public EnvironmentKeySupplier() {
        this(System::getenv);
    }

    public EnvironmentKeySupplier(final UnaryOperator<String> envReader) {
        this.envReader = envReader;
    }

    @Override
    public Optional<KeyPassword> getPassword(final String keyId) {
        return validateKeyId(keyId)
                .flatMap(this::readEnvironmentVariable)
                .flatMap(this::createKeyPassword);
    }

    private Optional<String> validateKeyId(final String keyId) {
        return (keyId == null || keyId.isBlank()) 
                ? Optional.empty() 
                : Optional.of(keyId);
    }

    private Optional<String> readEnvironmentVariable(final String keyId) {
        final String value = envReader.apply(keyId);
        return (value == null || value.isBlank()) 
                ? Optional.empty() 
                : Optional.of(value);
    }

    private Optional<KeyPassword> createKeyPassword(final String value) {
        final char[] chars = value.toCharArray();
        try {
            return Optional.of(KeyPassword.of(chars));
        } catch (final IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Failed to create KeyPassword from environment variable: {0}", e.getMessage());
            return Optional.empty();
        } finally {
            Arrays.fill(chars, '\0');
        }
    }
}
