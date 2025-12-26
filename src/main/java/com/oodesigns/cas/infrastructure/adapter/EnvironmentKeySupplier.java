package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.KeyPassword;

import java.util.Arrays;
import java.util.Optional;

/**
 * KeySupplier implementation that reads secrets from environment variables.
 * Retrieves the secret on-demand and converts to KeyPassword.
 * Uses the keyId parameter as the environment variable name.
 */
public final class EnvironmentKeySupplier implements KeySupplier {

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
        final String value = System.getenv(keyId);
        return (value == null || value.isBlank()) 
                ? Optional.empty() 
                : Optional.of(value);
    }

    private Optional<KeyPassword> createKeyPassword(final String value) {
        final char[] chars = value.toCharArray();
        try {
            return Optional.of(KeyPassword.of(chars));
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        } finally {
            Arrays.fill(chars, '\0');
        }
    }
}
