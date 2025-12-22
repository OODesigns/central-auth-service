package com.oodesigns.cas.application.command;

import java.util.Arrays;
import java.util.Objects;

/**
 * Application command for login use case.
 * Shaped for the use case; validates inputs.
 * 
 * <h2>Purpose</h2>
 * <p>Represents a complete login request with all necessary authentication data.
 * This command encapsulates user credentials and context information required
 * to authenticate a user and detect suspicious login patterns.</p>
 * 
 * <h2>Field Descriptions</h2>
 * 
 * <h3>username</h3>
 * <p>The user's unique identifier (e.g., "john_doe", "admin@company.com").
 * Required for: looking up the user in the database to retrieve their password hash
 * and permissions. Must not be null or blank.</p>
 * 
 * <h3>passwordChars</h3>
 * <p>The plaintext password provided by the user as a char array.
 * <strong>Security Note:</strong> Stored as char[] (not String) because char arrays
 * can be explicitly zeroed in memory after verification, reducing the window where
 * the plaintext password exists in memory. This is compared against the stored
 * password hash using a secure hashing algorithm. Must not be null or empty.
 * The char array is cloned to prevent external modification.</p>
 * 
 * <h3>ipAddress</h3>
 * <p>The client's IP address (e.g., "192.168.1.100", "203.0.113.45").
 * <strong>Why it's needed:</strong>
 * <ul>
 *   <li><strong>Rate Limiting:</strong> Prevents brute force attacks by limiting login
 *       attempts per IP address (e.g., max 5 attempts per 15 minutes)</li>
 *   <li><strong>Fraud Detection:</strong> Detects suspicious patterns like logins from
 *       geographically impossible locations in short time spans</li>
 *   <li><strong>Audit Logging:</strong> Records where login attempts originated for
 *       security investigations</li>
 *   <li><strong>Access Control:</strong> Restricts access to trusted IP ranges for
 *       sensitive accounts</li>
 * </ul>
 * Must not be null or blank.</p>
 * 
 * 
 * <h2>Immutability & Security</h2>
 * <ul>
 *   <li>This is a record (immutable after construction)</li>
 *   <li>Password char array is cloned both on construction and when retrieved</li>
 *   <li>All fields are validated as non-null and non-blank</li>
 *   <li>toString() masks the password for safe logging</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>
 * char[] password = "userPassword123".toCharArray();
 * try {
 *     LoginCommand cmd = new LoginCommand(
 *         "john_doe",
 *         password,
 *         "192.168.1.100"
 *     );
 *     // Use cmd in authentication flow
 * } finally {
 *     Arrays.fill(password, '\0'); // Securely clear plaintext password
 * }
 * </pre>
 * 
 * @param username User's unique identifier for lookup in the system
 * @param passwordChars Plaintext password as char array for secure memory handling
 * @param ipAddress Client's IP address for rate limiting and fraud detection
 */
public record LoginCommand(String username, char[] passwordChars, String ipAddress) {
    
    /**
     * Constructs a validated LoginCommand.
     * 
     * <p>All parameters are validated to ensure they are non-null and non-blank.
     * The password char array is cloned to prevent external modification.</p>
     * 
     * @param username The user's login identifier; must not be null or blank
     * @param passwordChars The password as char array; must not be null or empty.
     *                      Will be cloned internally for security
     * @param ipAddress The client's source IP address; must not be null or blank.
     *                  Used for rate limiting and fraud detection
     * @throws IllegalArgumentException if any parameter is null or blank
     */
    public LoginCommand(final String username, final char[] passwordChars, final String ipAddress) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (passwordChars == null || passwordChars.length == 0) {
            throw new IllegalArgumentException("Password is required");
        }
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException("IP address is required");
        }

        this.username = username;
        this.passwordChars = passwordChars.clone();
        this.ipAddress = ipAddress;
    }

    /**
     * Returns a clone of the password char array.
     * 
     * <p>The array is cloned on each access to prevent callers from modifying
     * the internal password representation, ensuring immutability.</p>
     * 
     * @return A copy of the password char array
     */
    @Override
    public char[] passwordChars() {
        return passwordChars.clone();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final LoginCommand that = (LoginCommand) o;
        return Objects.equals(username, that.username)
                && Arrays.equals(passwordChars, that.passwordChars)
                && Objects.equals(ipAddress, that.ipAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, Arrays.hashCode(passwordChars), ipAddress);
    }

    @Override
    public String toString() {
        return "LoginCommand{"
                + "username='" + username + '\''
                + ", passwordChars=***"
                + ", ipAddress='" + ipAddress + '\''
                + '}';
    }
}
