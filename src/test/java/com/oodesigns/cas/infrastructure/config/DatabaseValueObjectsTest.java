package com.oodesigns.cas.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseValueObjectsTest {

    @Test
    void databaseHostAcceptsValidValuesAndTrims() {
        final DatabaseHost host = DatabaseHost.of(" db.example.com ");
        assertEquals("db.example.com", host.value());
    }

    @Test
    void databaseHostRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseHost.of(null));
        assertThrows(IllegalArgumentException.class, () -> DatabaseHost.of("   "));
        assertThrows(IllegalArgumentException.class, () -> DatabaseHost.of("invalid..host"));
        assertThrows(IllegalArgumentException.class, () -> DatabaseHost.of("host with spaces"));
    }

    @Test
    void databasePortAcceptsBounds() {
        assertEquals(1, DatabasePort.of("1").value());
        assertEquals(65_535, DatabasePort.of("65535").value());
    }

    @Test
    void databasePortRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> DatabasePort.of(null));
        assertThrows(IllegalArgumentException.class, () -> DatabasePort.of(""));
        assertThrows(IllegalArgumentException.class, () -> DatabasePort.of("abc"));
        assertThrows(IllegalArgumentException.class, () -> DatabasePort.of("0"));
        assertThrows(IllegalArgumentException.class, () -> DatabasePort.of("70000"));
    }

    @Test
    void databaseNameAcceptsValidValue() {
        assertEquals("auth_db", DatabaseName.of("auth_db").value());
    }

    @Test
    void databaseNameRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseName.of(null));
        assertThrows(IllegalArgumentException.class, () -> DatabaseName.of(""));
        assertThrows(IllegalArgumentException.class, () -> DatabaseName.of("1db"));
        assertThrows(IllegalArgumentException.class, () -> DatabaseName.of("db name"));
    }

    @Test
    void databaseUserAcceptsValidValue() {
        assertEquals("app_user", DatabaseUser.of("app_user").value());
    }

    @Test
    void databaseUserRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseUser.of(null));
        assertThrows(IllegalArgumentException.class, () -> DatabaseUser.of(""));
        assertThrows(IllegalArgumentException.class, () -> DatabaseUser.of("123user"));
        assertThrows(IllegalArgumentException.class, () -> DatabaseUser.of("user name"));
    }

    @Test
    void databasePasswordAllowsValidValue() {
        final char[] source = "SecureP@ss1".toCharArray();
        try (final DatabasePassword password = DatabasePassword.of(source)) {
            Arrays.fill(source, 'x');
            assertArrayEquals("SecureP@ss1".toCharArray(), password.chars());
            assertEquals("DatabasePassword{***}", password.toString());
        }
    }

    @Test
    void databasePasswordReturnsDefensiveCopiesAndClearsOnClose() {
        final DatabasePassword password = DatabasePassword.of("SecureP@ss1");
        final char[] firstCopy = password.chars();
        Arrays.fill(firstCopy, 'x');

        assertArrayEquals("SecureP@ss1".toCharArray(), password.chars());

        password.close();
        assertArrayEquals(new char[11], password.chars());
    }

    @Test
    void databasePasswordRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> DatabasePassword.of((String) null));
        assertThrows(IllegalArgumentException.class, () -> DatabasePassword.of((char[]) null));
    }

    @Test
    void databasePasswordRejectsTooShort() {
        assertThrows(IllegalArgumentException.class, () -> DatabasePassword.of("Short@1"));
    }

    @Test
    void databasePasswordRejectsNoUppercase() {
        assertThrows(IllegalArgumentException.class, () -> DatabasePassword.of("no-uppercase@1234"));
    }

    @Test
    void databasePasswordRejectsNoDigit() {
        assertThrows(IllegalArgumentException.class, () -> DatabasePassword.of("NoDigit@Pass"));
    }

    @Test
    void databasePasswordRejectsNoSpecialChar() {
        assertThrows(IllegalArgumentException.class, () -> DatabasePassword.of("NoSpecial1234"));
    }
}
