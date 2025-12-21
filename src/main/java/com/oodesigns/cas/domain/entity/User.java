package com.oodesigns.cas.domain.entity;

import com.oodesigns.cas.domain.value.PasswordHash;
import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.Role;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.Username;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Domain entity representing a user.
 * Immutable; changes trigger new state via builders or domain methods.
 */
public final class User {
    private final UserId userId;
    private final Username username;
    private final PasswordHash passwordHash;
    private final Set<Role> roles;
    private final Set<Permission> permissions;
    private final boolean forcePasswordReset;
    private final Instant createdAt;
    private final Instant updatedAt;

    private User(final UserId userId, final Username username, final PasswordHash passwordHash,
                 final Set<Role> roles, final Set<Permission> permissions, final boolean forcePasswordReset,
                 final Instant createdAt, final Instant updatedAt) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = Collections.unmodifiableSet(new HashSet<>(roles));
        this.permissions = Collections.unmodifiableSet(new HashSet<>(permissions));
        this.forcePasswordReset = forcePasswordReset;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static User create(final UserId userId, final Username username, final PasswordHash passwordHash) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(passwordHash, "passwordHash cannot be null");
        return new User(userId, username, passwordHash, new HashSet<>(), new HashSet<>(), true, Instant.now(), Instant.now());
    }

    public static User restore(final UserId userId, final Username username, final PasswordHash passwordHash,
                               final Set<Role> roles, final Set<Permission> permissions, final boolean forcePasswordReset,
                               final Instant createdAt, final Instant updatedAt) {
        return new User(userId, username, passwordHash, roles, permissions, forcePasswordReset, createdAt, updatedAt);
    }

    public User assignRole(final Role role) {
        Set<Role> newRoles = this.roles.stream()
            .collect(Collectors.toCollection(HashSet::new));
        newRoles.add(role);
        return new User(this.userId, this.username, this.passwordHash, newRoles, this.permissions,
                       this.forcePasswordReset, this.createdAt, Instant.now());
    }

    public User grantPermission(final Permission permission) {
        Set<Permission> newPermissions = this.permissions.stream()
            .collect(Collectors.toCollection(HashSet::new));
        newPermissions.add(permission);
        return new User(this.userId, this.username, this.passwordHash, this.roles, newPermissions,
                       this.forcePasswordReset, this.createdAt, Instant.now());
    }

    public User revokePermission(final Permission permission) {
        Set<Permission> newPermissions = this.permissions.stream()
            .filter(p -> !p.equals(permission))
            .collect(Collectors.toCollection(HashSet::new));
        return new User(this.userId, this.username, this.passwordHash, this.roles, newPermissions,
                       this.forcePasswordReset, this.createdAt, Instant.now());
    }

    public User clearForcePasswordReset() {
        return new User(this.userId, this.username, this.passwordHash, this.roles, this.permissions,
                       false, this.createdAt, Instant.now());
    }

    public boolean hasRole(final Role role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole(Role.admin());
    }

    // Getters
    public UserId getUserId() {
        return userId;
    }

    public Username getUsername() {
        return username;
    }

    public PasswordHash getPasswordHash() {
        return passwordHash;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(final Permission permission) {
        return permissions.contains(permission);
    }

    public boolean isForcePasswordReset() {
        return forcePasswordReset;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return Objects.equals(userId, user.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", username=" + username +
                '}';
    }
}
