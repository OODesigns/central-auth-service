package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.IpAddress;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.Username;
import java.util.Objects;

/**
 * Login command with validated username, password, and IP address.
 * Immutable record that encapsulates all authentication data required for login.
 * 
 * @param username User's validated login identifier
 * @param password User's validated password  
 * @param ipAddress Client's validated IP address for rate limiting and fraud detection
 */
public record LoginCommand(Username username, Password password, IpAddress ipAddress) {
    
    /**
     * Constructs a validated LoginCommand.
     * Validates that all fields are non-null.
     * 
     * @param username Non-null validated username
     * @param password Non-null validated password
     * @param ipAddress Non-null validated IP address
     * @throws NullPointerException if any parameter is null
     */
    public LoginCommand {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(password, "Password is required");
        Objects.requireNonNull(ipAddress, "IP address is required");
    }
}
