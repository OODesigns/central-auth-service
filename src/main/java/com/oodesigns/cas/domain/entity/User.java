package com.oodesigns.cas.domain.entity;

import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.Username;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Domain entity representing an authenticated user for authorization.
 * Immutable and minimal - contains only what's needed after authentication.
 * Password hash is NOT stored here; use UserCredential for authentication.
 */
public record User(UserId userId, Username username, Set<Permission> permissions) {
    public User {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(permissions, "permissions cannot be null");
        // Make permissions unmodifiable
        permissions = Collections.unmodifiableSet(new HashSet<>(permissions));
    }

    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", username=" + username +
                '}';
    }
}
