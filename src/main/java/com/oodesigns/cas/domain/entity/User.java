package com.oodesigns.cas.domain.entity;

import com.oodesigns.cas.domain.value.PasswordHash;
import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.Username;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Domain entity representing a user for authentication.
 * Immutable and minimal - contains only what's needed for login.
 */
public record User(UserId userId, Username username, PasswordHash passwordHash, 
                   Set<Permission> permissions) {
    public User {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(passwordHash, "passwordHash cannot be null");
        Objects.requireNonNull(permissions, "permissions cannot be null");
        // Make permissions unmodifiable
        permissions = Collections.unmodifiableSet(new HashSet<>(permissions));
    }

    public static User create(final UserId userId, final Username username, final PasswordHash passwordHash) {
        return new User(userId, username, passwordHash, new HashSet<>());
    }

    public User grantPermission(final Permission permission) {
        Set<Permission> newPermissions = new HashSet<>(this.permissions);
        newPermissions.add(permission);
        return new User(this.userId, this.username, this.passwordHash, newPermissions);
    }

    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", username=" + username +
                '}';
    }
}
