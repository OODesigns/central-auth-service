package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.RefreshToken;

/**
 * Command to exchange a valid refresh token for a fresh access + refresh token pair.
 * <p>
 * The presented refresh token is rotated: it is consumed and replaced by a new token in the
 * same family. Replaying an already-rotated token triggers reuse detection.
 *
 * @param refreshToken the raw refresh token issued by a previous login / 2FA verification / refresh
 */
public record RefreshTokenCommand(RefreshToken refreshToken) {
    public RefreshTokenCommand {
        java.util.Objects.requireNonNull(refreshToken, "Refresh token is required");
    }

    public RefreshTokenCommand(final String refreshToken) {
        this(RefreshToken.of(refreshToken));
    }
}

