package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.UserId;
import java.util.Objects;

/** Administrative command to create recovery material for a target account. */
public record IssueRecoveryTokenCommand(UserId administratorId, UserId targetUserId) {
    public IssueRecoveryTokenCommand {
        Objects.requireNonNull(administratorId, "Administrator ID is required");
        Objects.requireNonNull(targetUserId, "Target user ID is required");
    }
}