package com.oodesigns.cas.application.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefreshTokenCommandTest {

    @Test
    void constructorAcceptsNonBlankToken() {
        final RefreshTokenCommand command = new RefreshTokenCommand("some.refresh.token");
        assertEquals("some.refresh.token", command.refreshToken());
    }

    @Test
    void constructorRejectsNullToken() {
        assertThrows(IllegalArgumentException.class, () -> new RefreshTokenCommand(null));
    }

    @Test
    void constructorRejectsBlankToken() {
        assertThrows(IllegalArgumentException.class, () -> new RefreshTokenCommand("   "));
    }

    @Test
    void constructorRejectsEmptyToken() {
        assertThrows(IllegalArgumentException.class, () -> new RefreshTokenCommand(""));
    }
}

