package com.oodesigns.cas.domain.value;

import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Value object representing a plaintext password.
 * Stores password as char[] for secure memory handling.
 * Ensures password can be zeroed out from memory after use.
 * Implements the ValidatedValue pattern: parse then validate.
 * 
 * Implements AutoCloseable to support try-with-resources for automatic cleanup.
 */
public class Password implements AutoCloseable {
    private final char[] passwordChars;

    /**
     * Create a Password from a char array.
     * The provided char array is cloned internally for security.
     * Only performs null/empty validation; subclasses must validate via factory methods.
     *
     * @param passwordChars the plaintext password as char array
     * @throws NullPointerException if password is null
     * @throws IllegalArgumentException if password is empty
     */
    public Password(final char[] passwordChars) {
        Objects.requireNonNull(passwordChars, "Password cannot be null");
        if (passwordChars.length == 0) {
            throw new IllegalArgumentException("Password cannot be empty");
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
     * Closes this resource and securely clears the password from memory.
     * Implements AutoCloseable to support try-with-resources.
     */
    @Override
    public void close() {
        clear();
    }

    /**
     * Create a Password from a char array.
     * 
     * @param passwordChars the plaintext password as char array
     * @return a new Password instance
     * @throws NullPointerException if passwordChars is null
     * @throws IllegalArgumentException if passwordChars is empty
     */
    public static Password of(final char[] passwordChars) {
        return new Password(passwordChars);
    }

    @Nonnull
    @Override
    public String toString() {
        return "Password{***}";
    }

    /**
     * Create a Password from a String.
     * Strings are immutable and cannot be zeroed from memory.
     * Use the char[] constructor/factory method when possible.
     * <p>
     * NOTE: This method is primarily for testing. In production, avoid creating
     * Password from String since the String parameter cannot be cleared from memory.
     *
     * @param password the plaintext password as String
     * @return a new Password instance
     * @throws NullPointerException if password is null
     * @throws IllegalArgumentException if password is empty
     */
    public static Password of(final String password) {
        Objects.requireNonNull(password, "Password cannot be null");
        return Password.of(password.toCharArray());
    }
}
