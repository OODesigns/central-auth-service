package com.oodesigns.cas.domain.value;


import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
/**
 * Value object representing a plaintext password.
 * Stores password as char[] for secure memory handling.
 * Enforces modern password security standards:
 * - Minimum length: 14 characters
 * - Maximum length: 128+ characters
 * - Allows all printable characters (including spaces)
 * - No mandatory complexity rules
 * - Case-sensitive
 * <p>
 * Ensures password can be zeroed out from memory after use.
 * Implements AutoCloseable to support try-with-resources for automatic cleanup.
 * Uses functional composition for validation pipeline.
 */
public class Password implements AutoCloseable {
    private static final String DEFAULT_TYPE = "Password";
    private final char[] passwordChars;

    // Modern password validation constants
    private static final int MIN_LENGTH = 14;
    private static final int MAX_LENGTH = 128;

    /**
     * Create a Password from a char array with validation using functional composition.
     * Chains validation functions together in a pipeline: identity -> length -> content -> clone.
     * Protected constructor - use factory methods for validated construction.
     * Protected access allows subclasses like KeyPassword to call super().
     *
     * @param passwordChars the plaintext password as char array (already validated)
     */
    protected Password(final char[] passwordChars) {
        // Simple assignment - validation is done by factory methods
        this.passwordChars = passwordChars;
    }

     /**
     * Validates password length requirements.
     * Static method for use in validation pipeline.
     *
     * @param passwordChars the password to validate
     * @param typeName the type name for error messages (e.g., "Password", "Key Password")
     * @return the char array if validation passes
     * @throws IllegalArgumentException if length is invalid
     */
     protected static char[] validateLength(final char[] passwordChars, final String typeName) {
        if (passwordChars.length < MIN_LENGTH) {
            throw new IllegalArgumentException(
                String.format("%s must be at least %d characters long (currently %d)",
                    typeName, MIN_LENGTH, passwordChars.length)
            );
        }
        if (passwordChars.length > MAX_LENGTH) {
            throw new IllegalArgumentException(
                String.format("%s must not exceed %d characters (currently %d)",
                    typeName, MAX_LENGTH, passwordChars.length)
            );
        }
        return passwordChars;
    }
        
    private static char[] validateLength(final char[] passwordChars) {    
           return validateLength(passwordChars, DEFAULT_TYPE);   
    }

    /**
     * Validates password content against security rules.
     * Static method for use in validation pipeline.
     *
     * @param passwordChars the password to validate
     * @param typeName the type name for error messages (e.g., "Password", "Key Password")
     * @return the char array if validation passes
     * @throws IllegalArgumentException if password violates security rules
     */
    protected static char[] validateContent(final char[] passwordChars, final String typeName) {
        // Ensure password is not blank (prevent spaces-only passwords)
        for (final char c : passwordChars) {
            if (!Character.isWhitespace(c)) {
                // Found at least one non-whitespace character, password is valid
                return passwordChars;
            }
        }
        // All characters are whitespace - invalid
        throw new IllegalArgumentException(String.format("%s cannot contain only spaces", typeName));
    }

    private static char[] validateContent(final char[] passwordChars) {
        return validateContent(passwordChars, DEFAULT_TYPE);
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
     * Safe to call multiple times.
     */
    @Override
    public void close() {
        clear();
    }

    /**
     * Create a Password from a char array with validation.
     * All validation occurs in this factory method.
     *
     * @param passwordChars the plaintext password as char array
     * @return a new Password instance
     * @throws NullPointerException if passwordChars is null
     * @throws IllegalArgumentException if password fails validation
     */
    public static Password of(final char[] passwordChars) {
        Objects.requireNonNull(passwordChars, "Password cannot be null");
        
        // Build validation pipeline: identity -> length -> content -> create
        return Function.<char[]>identity()
            .andThen(Password::validateLength)
            .andThen(Password::validateContent)
            .andThen(chars -> chars.clone())
            .andThen(Password::new)
            .apply(passwordChars);
    }

    @Override
    public String toString() {
        return "Password{***}";
    }

    /**
     * Create a Password from a String with validation.
     * Strings are immutable and cannot be zeroed from memory.
     * Use the char[] constructor/factory method when possible.
     * <p>
     * NOTE: This method is primarily for testing. In production, avoid creating
     * Password from String since the String parameter cannot be cleared from memory.
     *
     * @param password the plaintext password as String
     * @return a new Password instance
     * @throws NullPointerException if password is null
     * @throws IllegalArgumentException if password fails validation
     */
    public static Password of(final String password) {
        Objects.requireNonNull(password, "Password cannot be null");
        return Password.of(password.toCharArray());
    }
}





