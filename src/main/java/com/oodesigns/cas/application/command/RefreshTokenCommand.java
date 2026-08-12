package com.oodesigns.cas.application.command;

/**
 * Command to exchange a valid refresh token for a fresh access + refresh token pair.
 * <p>
 * The presented refresh token is rotated: it is consumed and replaced by a new token in the
 * same family. Replaying an already-rotated token triggers reuse detection.
 *
 * @param refreshToken the raw refresh token issued by a previous login / 2FA verification / refresh
 */
public record RefreshTokenCommand(String refreshToken) {
    public RefreshTokenCommand {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }
    }
}

