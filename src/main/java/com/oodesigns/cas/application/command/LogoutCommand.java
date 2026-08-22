package com.oodesigns.cas.application.command;

/**
 * Command for logging out an access-token session.
 */
public record LogoutCommand(String accessToken) {
    public LogoutCommand {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token is required");
        }
    }
}