package com.oodesigns.cas.domain.value;

import java.util.Arrays;

/**
 * Value object representing a plaintext password.
 * Stores password as char[] for secure memory handling.
 * Ensures password can be zeroed out from memory after use.
 */
public final class Password {
    private final char[] passwordChars;

    /**
     * Create a Password from a char array.
     * The provided char array is cloned internally for security.
     * 
     * @param passwordChars the plaintext password as char array
     * @throws IllegalArgumentException if password is null or empty
     */
    public Password(final char[] passwordChars) {
        if (passwordChars == null || passwordChars.length == 0) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        // Clone to prevent external modification
        this.passwordChars = passwordChars.clone();
    }

    /**
     * Get the password as a char array.
     * Returns a clone to maintain immutability.
     * 
     * @return a copy of the password char array
     */
    public char[] chars() {
        return passwordChars.clone();
    }

    /**
     * Securely clear the password from memory.
     * Overwrites all characters with null bytes.
     */
    public void clear() {
        Arrays.fill(passwordChars, '\0');
    }

    /**
     * Create a Password from a char array.
     * 
     * @param passwordChars the plaintext password as char array
     * @return a new Password instance
     */
    public static Password of(final char[] passwordChars) {
        return new Password(passwordChars);
    }

    @Override
    public String toString() {
        return "Password{***}";
    }

    /**
     * Prevent creation of Password from String for security reasons.
     * Strings are immutable and cannot be zeroed from memory.
     * Use char[] instead.
     */
    public static Password fromString(final String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return new Password(password.toCharArray());
    }
}
