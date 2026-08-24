package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.AccessToken;

/**
 * Command for logging out an access-token session.
 */
public record LogoutCommand(AccessToken accessToken) {
    public LogoutCommand {
        java.util.Objects.requireNonNull(accessToken, "Access token is required");
    }

    public LogoutCommand(final String accessToken) {
        this(AccessToken.of(accessToken));
    }
}