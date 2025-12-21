package com.oodesigns.cas.application.command;

/**
 * Application command for login use case.
 * Shaped for the use case; validates inputs.
 */
public final class LoginCommand {
    private final String username;
    private final char[] passwordChars;
    private final String ipAddress;
    private final String userAgent;

    public LoginCommand(final String username, final char[] passwordChars, final String ipAddress, final String userAgent) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (passwordChars == null || passwordChars.length == 0) {
            throw new IllegalArgumentException("Password is required");
        }
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException("IP address is required");
        }
        if (userAgent == null || userAgent.isBlank()) {
            throw new IllegalArgumentException("User agent is required");
        }
        
        this.username = username;
        this.passwordChars = passwordChars.clone();
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public String getUsername() {
        return username;
    }

    public char[] getPasswordChars() {
        return passwordChars.clone();
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
