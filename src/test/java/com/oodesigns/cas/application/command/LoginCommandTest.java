package com.oodesigns.cas.application.command;

import org.junit.jupiter.api.Test;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LoginCommand application command.
 * Validates: validation, password char cloning, immutability.
 */
public class LoginCommandTest {

    @Test
    public void testValidCommand() {
        char[] password = "password123".toCharArray();
        LoginCommand cmd = new LoginCommand("john_doe", password, "192.168.1.1", "Mozilla/5.0");

        assertEquals("john_doe", cmd.getUsername());
        assertEquals("192.168.1.1", cmd.getIpAddress());
        assertEquals("Mozilla/5.0", cmd.getUserAgent());
        // Verify password is cloned
        assertNotSame(password, cmd.getPasswordChars());
        assertArrayEquals(password, cmd.getPasswordChars());
    }

    @Test
    public void testNullUsernameThrows() {
        char[] password = "password123".toCharArray();
        assertThrows(IllegalArgumentException.class,
            () -> new LoginCommand(null, password, "192.168.1.1", "Mozilla/5.0"));
    }

    @Test
    public void testEmptyUsernameThrows() {
        char[] password = "password123".toCharArray();
        assertThrows(IllegalArgumentException.class,
            () -> new LoginCommand("", password, "192.168.1.1", "Mozilla/5.0"));
    }

    @Test
    public void testNullPasswordThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new LoginCommand("john_doe", null, "192.168.1.1", "Mozilla/5.0"));
    }

    @Test
    public void testEmptyPasswordThrows() {
        char[] password = new char[0];
        assertThrows(IllegalArgumentException.class,
            () -> new LoginCommand("john_doe", password, "192.168.1.1", "Mozilla/5.0"));
    }

    @Test
    public void testNullIpAddressThrows() {
        char[] password = "password123".toCharArray();
        assertThrows(IllegalArgumentException.class,
            () -> new LoginCommand("john_doe", password, null, "Mozilla/5.0"));
    }

    @Test
    public void testEmptyIpAddressThrows() {
        char[] password = "password123".toCharArray();
        assertThrows(IllegalArgumentException.class,
            () -> new LoginCommand("john_doe", password, "", "Mozilla/5.0"));
    }

    @Test
    public void testNullUserAgentThrows() {
        char[] password = "password123".toCharArray();
        assertThrows(IllegalArgumentException.class,
            () -> new LoginCommand("john_doe", password, "192.168.1.1", null));
    }

    @Test
    public void testEmptyUserAgentThrows() {
        char[] password = "password123".toCharArray();
        assertThrows(IllegalArgumentException.class,
            () -> new LoginCommand("john_doe", password, "192.168.1.1", ""));
    }

    @Test
    public void testPasswordCharArrayCloned() {
        char[] original = "password123".toCharArray();
        LoginCommand cmd = new LoginCommand("john_doe", original, "192.168.1.1", "Mozilla/5.0");
        
        // Modify original
        original[0] = 'X';
        
        // Command's password unchanged
        assertEquals('p', cmd.getPasswordChars()[0]);
    }

    @Test
    public void testPasswordCharArrayNotMutableViaGetter() {
        char[] password = "password123".toCharArray();
        LoginCommand cmd = new LoginCommand("john_doe", password, "192.168.1.1", "Mozilla/5.0");
        
        char[] retrieved = cmd.getPasswordChars();
        retrieved[0] = 'X';
        
        // Original command password unchanged
        assertEquals('p', cmd.getPasswordChars()[0]);
    }

    @Test
    public void testAllFieldsRequired() {
        char[] password = "password123".toCharArray();
        
        // All fields set correctly
        LoginCommand cmd = new LoginCommand("john_doe", password, "192.168.1.1", "Mozilla/5.0");
        assertNotNull(cmd.getUsername());
        assertNotNull(cmd.getPasswordChars());
        assertNotNull(cmd.getIpAddress());
        assertNotNull(cmd.getUserAgent());
    }

    @Test
    public void testMultipleInstancesHaveIndependentPasswords() {
        char[] pass1 = "password1".toCharArray();
        char[] pass2 = "password2".toCharArray();
        
        LoginCommand cmd1 = new LoginCommand("user1", pass1, "192.168.1.1", "Mozilla");
        LoginCommand cmd2 = new LoginCommand("user2", pass2, "192.168.1.2", "Chrome");
        
        assertFalse(Arrays.equals(cmd1.getPasswordChars(), cmd2.getPasswordChars()));
    }
}
