package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.IpAddress;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.Username;
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
public record LoginCommand(Username username, Password password, IpAddress ipAddress) {
    
    /**
     * Constructs a validated LoginCommand using value objects.
     * 
     * <p>Username, Password, and IpAddress are validated at construction
     * of their respective value objects, ensuring all constraints are met
     * before the LoginCommand is created.</p>
     * 
     * @param username The user's validated login identifier
     * @param password The user's validated password
     * @param ipAddress The client's validated IP address
     * @throws IllegalArgumentException if any parameter is null
     */
    public LoginCommand {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(password, "Password is required");
        Objects.requireNonNull(ipAddress, "IP address is required");
    }

    @Override
    public String toString() {
        return "LoginCommand{"
                + "username='" + username + '\''
                + ", password=***"
                + ", ipAddress='" + ipAddress + '\''
                + '}';
    }
}
