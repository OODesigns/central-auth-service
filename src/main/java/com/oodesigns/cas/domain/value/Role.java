package com.oodesigns.cas.domain.value;

import java.util.Arrays;
import java.util.Objects;

/**
 * Value object representing a role.
 * Roles are static configuration: admin, user, kiosk.
 */
public record Role(RoleName name) {
    public Role {
        Objects.requireNonNull(name, "Role name cannot be null");
    }

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

    public static Role admin() {
        return new Role(RoleName.ADMIN);
    }

    public static Role user() {
        return new Role(RoleName.USER);
    }

    public static Role kiosk() {
        return new Role(RoleName.KIOSK);
    }

    public String asString() {
        return name.getValue();
    }

    @Override
    public String toString() {
        return name.getValue();
    }
}
