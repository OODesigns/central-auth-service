package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Credentials;
import com.oodesigns.cas.domain.value.PasswordHash;
import com.oodesigns.cas.domain.entity.User;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Mock implementation of PasswordHasher for testing.
 * Tracks hashed passwords and verifies them correctly for testing purposes.
 */
public class MockPasswordHasher implements Ports.PasswordHasher {
    private final Map<String, String> passwordMap = new HashMap<>();
    private int hashCounter = 0;

    @Override
    public PasswordHash hash(char[] rawPassword) {
        if (rawPassword == null || rawPassword.length == 0) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        // Generate bcrypt-formatted mock hash: $2a$12$<random 53 chars>
        // Format: $2a$cost$salt$hash
        // We use a deterministic format for testing
        String salt = String.format("%053d", hashCounter);
        String hashValue = String.format("$2a$12$%s", salt);
        
        // Store mapping for verification
        passwordMap.put(hashValue, new String(rawPassword));
        hashCounter++;
        
        return new PasswordHash(hashValue);
    }

    @Override
    public Optional<User> verify(final Credentials credentials) {
        if (credentials == null) {
            throw new IllegalArgumentException("Credentials cannot be null");
        }
        
        String storedPassword = passwordMap.get(credentials.user().passwordHash().asString());
        if (storedPassword != null && storedPassword.equals(new String(credentials.password().chars()))) {
            return Optional.of(credentials.user());
        }
        return Optional.empty();
    }

    public void clear() {
        passwordMap.clear();
        hashCounter = 0;
    }
}
