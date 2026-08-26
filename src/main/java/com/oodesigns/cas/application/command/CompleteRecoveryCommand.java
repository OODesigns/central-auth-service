package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.RecoveryToken;
import java.util.Objects;

/** Public completion command carrying a recovery-purpose token and replacement password. */
public record CompleteRecoveryCommand(RecoveryToken token, Password newPassword) {
    public CompleteRecoveryCommand {
        Objects.requireNonNull(token, "Recovery token is required");
        Objects.requireNonNull(newPassword, "New password is required");
    }
}