package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.UserId;

import java.util.Objects;

/** Administrative command that disables TOTP for a target user. */
public record AdminDisableTotpCommand(UserId adminId, Password adminPassword,
                                      UserId targetUserId, DisableReason reason) {
    public AdminDisableTotpCommand {
        Objects.requireNonNull(adminId, "Admin ID is required");
        Objects.requireNonNull(adminPassword, "Admin password is required");
        Objects.requireNonNull(targetUserId, "Target user ID is required");
        Objects.requireNonNull(reason, "Disable reason is required");
        if (reason == DisableReason.USER_REQUESTED) {
            throw new IllegalArgumentException("Administrative disable requires a privileged reason");
        }
    }
}