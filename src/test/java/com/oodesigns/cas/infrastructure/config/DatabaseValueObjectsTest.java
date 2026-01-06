package com.oodesigns.cas.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseValueObjectsTest {

    @Test
    void databaseHostAcceptsValidValuesAndTrims() {
        final DatabaseHost host = new DatabaseHost(" db.example.com ");
        assertEquals("db.example.com", host.value());
    }

    @Test
    void databaseHostRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseHost(null));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseHost("   "));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseHost("invalid..host"));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseHost("host with spaces"));
    }

    @Test
    void databasePortAcceptsBounds() {
        assertEquals(1, new DatabasePort("1").value());
        assertEquals(65_535, new DatabasePort("65535").value());
    }

    @Test
    void databasePortRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new DatabasePort(null));
        assertThrows(IllegalArgumentException.class, () -> new DatabasePort(""));
        assertThrows(IllegalArgumentException.class, () -> new DatabasePort("abc"));
        assertThrows(IllegalArgumentException.class, () -> new DatabasePort("0"));
        assertThrows(IllegalArgumentException.class, () -> new DatabasePort("70000"));
    }

    @Test
    void databaseNameAcceptsValidValue() {
        assertEquals("auth_db", new DatabaseName("auth_db").value());
    }

    @Test
    void databaseNameRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseName(null));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseName(""));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseName("1db"));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseName("db name"));
    }

    @Test
    void databaseUserAcceptsValidValue() {
        assertEquals("app_user", new DatabaseUser("app_user").value());
    }

    @Test
    void databaseUserRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseUser(null));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseUser(""));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseUser("123user"));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseUser("user name"));
    }

    @Test
    void databasePasswordAllowsValidValue() {
        assertEquals("SecureP@ss1", new DatabasePassword("SecureP@ss1").value());
    }

    @Test
    void databasePasswordRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new DatabasePassword(null));
    }

    @Test
    void databasePasswordRejectsTooShort() {
        assertThrows(IllegalArgumentException.class, () -> new DatabasePassword("Short@1"));
    }

    @Test
    void databasePasswordRejectsNoUppercase() {
        assertThrows(IllegalArgumentException.class, () -> new DatabasePassword("no-uppercase@1234"));
    }

    @Test
    void databasePasswordRejectsNoDigit() {
        assertThrows(IllegalArgumentException.class, () -> new DatabasePassword("NoDigit@Pass"));
    }

    @Test
    void databasePasswordRejectsNoSpecialChar() {
        assertThrows(IllegalArgumentException.class, () -> new DatabasePassword("NoSpecial1234"));
    }
}
