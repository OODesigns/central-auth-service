package com.oodesigns.cas.domain.value;

import java.util.Arrays;
import java.util.Objects;

/**
 * Value object representing a role.
 * Roles are static configuration: admin, user, kiosk.
 */
public final class Role {
    public enum RoleName {
        ADMIN("admin"),
        USER("user"),
        KIOSK("kiosk");

        private final String value;

        RoleName(String value) {
            this.value = value;
        }

        public static RoleName of(final String value) {
            return Arrays.stream(values())
                .filter(role -> role.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + value));
        }

        public String getValue() {
            return value;
        }
    }

    private final RoleName name;

    public Role(RoleName name) {
        this.name = Objects.requireNonNull(name, "Role name cannot be null");
    }

    public static Role admin() {
        return new Role(RoleName.ADMIN);
    }

    public static Role user() {
        return new Role(RoleName.USER);
    }

    public static Role kiosk() {
        return new Role(RoleName.KIOSK);
    }

    public RoleName getName() {
        return name;
    }

    public String asString() {
        return name.getValue();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Role)) return false;
        Role role = (Role) o;
        return name == role.name;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name.getValue();
    }
}
