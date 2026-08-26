package com.oodesigns.cas.testutil;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Generates a bcrypt hash for the disposable local database-test environment. */
public final class BcryptHashGenerator {
    private BcryptHashGenerator() {
    }

    public static void main(final String[] arguments) {
        if (arguments.length != 1 || arguments[0].isBlank()) {
            throw new IllegalArgumentException("A non-blank password is required");
        }
        System.out.println(new BCryptPasswordEncoder().encode(arguments[0]));
    }
}