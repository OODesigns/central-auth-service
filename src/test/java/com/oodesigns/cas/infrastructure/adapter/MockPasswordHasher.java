package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Credentials;
import com.oodesigns.cas.domain.value.UserId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Mock implementation of PasswordVerifier for testing.
 * Tracks hashed passwords and verifies them correctly for testing purposes.
 */
public class MockPasswordHasher implements Ports.PasswordVerifier {
    private final Map<String, String> passwordMap = new HashMap<>();

    /**
     * Register a password hash and raw password mapping for testing.
     * Allows tests to set up expected password verification scenarios.
     * 
     * @param hashValue The hashed password
     * @param rawPassword The raw password to match against
     */
    public void registerPasswordHash(final String hashValue, final String rawPassword) {
        if (hashValue == null || hashValue.isEmpty()) {
            throw new IllegalArgumentException("Hash value cannot be null or empty");
        }
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Raw password cannot be null or empty");
        }
        passwordMap.put(hashValue, rawPassword);
    }

    @Override
    public Optional<UserId> verify(final Credentials credentials) {
        if (credentials == null) {
            throw new IllegalArgumentException("Credentials cannot be null");
        }
        
        String storedPassword = passwordMap.get(credentials.credential().passwordHash().asString());
        if (storedPassword != null && storedPassword.equals(new String(credentials.password().chars()))) {
            return Optional.of(credentials.credential().userId());
        }
        return Optional.empty();
    }

    public void clear() {
        passwordMap.clear();
    }
}
