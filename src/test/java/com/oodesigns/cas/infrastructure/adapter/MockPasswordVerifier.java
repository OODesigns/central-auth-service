package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Credentials;
import com.oodesigns.cas.domain.value.PasswordHash;
import com.oodesigns.cas.domain.value.UserId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Mock implementation of PasswordVerifier for testing.
 * Tracks hashed passwords and verifies them correctly for testing purposes.
 */
public class MockPasswordVerifier implements Ports.PasswordVerifier {
    private final Map<String, String> passwordMap = new HashMap<>();

    /**
     * Register a password hash for verification testing.
     * Used in test setup to simulate a stored password hash.
     * 
     * @param hash The bcrypt hash string
     * @param plainPassword The original password (for mocking verification)
     */
    public void registerPasswordHash(final String hash, final String plainPassword) {
        passwordMap.put(hash, plainPassword);
    }

    /**
     * Create a password hash for testing purposes.
     * This simulates password hashing in test setup.
     * 
     * @param rawPassword The plain text password to hash
     * @return A PasswordHash object that can be stored in test users
     */
    public PasswordHash hash(final char[] rawPassword) {
        // For testing, create a mock bcrypt-formatted hash
        // Bcrypt format: $2a$10$SS... (22 chars salt) + U... (31 chars hash)
        final String uuid = UUID.randomUUID().toString().replace("-", "");
        final String salt = uuid.substring(0, 22);  // 22 chars for salt
        final String hashPart = (uuid + uuid).substring(0, 31);  // 31 chars for hash
        final String mockBcryptHash = "$2a$10$%s%s".formatted(salt, hashPart);
        registerPasswordHash(mockBcryptHash, new String(rawPassword));
        return new PasswordHash(mockBcryptHash);
    }

    @Override
    public Optional<UserId> verify(final Credentials credentials) {
        if (credentials == null) {
            throw new IllegalArgumentException("Credentials cannot be null");
        }
        
        String storedPassword = passwordMap.get(credentials.credential().passwordHash().value());
        if (storedPassword != null && storedPassword.equals(new String(credentials.password().chars()))) {
            return Optional.of(credentials.credential().userId());
        }
        return Optional.empty();
    }

    /**
     * Clear all registered password mappings (test helper).
     */
    public void clear() {
        passwordMap.clear();
    }

}

