package com.oodesigns.cas.application.command;

import org.junit.jupiter.api.Test;
import com.oodesigns.cas.domain.value.RefreshToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefreshTokenCommandTest {

    @Test
    void constructorAcceptsNonBlankToken() {
        final RefreshTokenCommand command = new RefreshTokenCommand("some.refresh.token");
        assertEquals("some.refresh.token", command.refreshToken().value());
    }

    @Test
    void constructorRejectsNullToken() {
        assertThrows(NullPointerException.class, () -> new RefreshTokenCommand((RefreshToken) null));
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

