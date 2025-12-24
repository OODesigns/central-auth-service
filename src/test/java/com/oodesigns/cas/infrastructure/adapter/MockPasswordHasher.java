package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.PasswordHash;
import java.util.HashMap;
import java.util.Map;

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
    public boolean verify(char[] rawPassword, PasswordHash hash) {
        if (rawPassword == null || hash == null) {
            throw new IllegalArgumentException("Password and hash cannot be null");
        }
        // Look up the original password that was hashed
        String storedPassword = passwordMap.get(hash.asString());
        return storedPassword != null && storedPassword.equals(new String(rawPassword));
    }

    public void clear() {
        passwordMap.clear();
        hashCounter = 0;
    }
}
