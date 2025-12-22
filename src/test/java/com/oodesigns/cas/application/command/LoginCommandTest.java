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
        LoginCommand cmd = new LoginCommand("john_doe", password, "192.168.1.1");

        assertEquals("john_doe", cmd.username());
        assertEquals("192.168.1.1", cmd.ipAddress());
        // Verify password is cloned
        assertNotSame(password, cmd.passwordChars());
        assertArrayEquals(password, cmd.passwordChars());
    }

    @Test
    public void testNullUsernameThrows() {
        char[] password = "password123".toCharArray();
        assertThrows(IllegalArgumentException.class,
            () -> new LoginCommand(null, password, "192.168.1.1"));
    }

    @Test
    public void testEmptyUsernameThrows() {
        char[] password = "password123".toCharArray();
        assertThrows(IllegalArgumentException.class,
            () -> new LoginCommand("", password, "192.168.1.1"));
    }

    @Test
    public void testNullPasswordThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new LoginCommand("john_doe", null, "192.168.1.1"));
    }

    @Test
    public void testEmptyPasswordThrows() {
        char[] password = new char[0];
        assertThrows(IllegalArgumentException.class,
            () -> new LoginCommand("john_doe", password, "192.168.1.1"));
    }

    @Test
    public void testNullIpAddressThrows() {
        char[] password = "password123".toCharArray();
        assertThrows(IllegalArgumentException.class,
            () -> new LoginCommand("john_doe", password, null));
    }

    @Test
    public void testEmptyIpAddressThrows() {
        char[] password = "password123".toCharArray();
        assertThrows(IllegalArgumentException.class,
            () -> new LoginCommand("john_doe", password, ""));
    }

    @Test
    public void testNullUserAgentThrows() {
        // UserAgent field removed - no longer required
    }

    @Test
    public void testEmptyUserAgentThrows() {
        // UserAgent field removed - no longer required
    }

    @Test
    public void testPasswordCharArrayCloned() {
        char[] original = "password123".toCharArray();
        LoginCommand cmd = new LoginCommand("john_doe", original, "192.168.1.1");
        
        // Modify original
        original[0] = 'X';
        
        // Command's password unchanged
        assertEquals('p', cmd.passwordChars()[0]);
    }

    @Test
    public void testPasswordCharArrayNotMutableViaGetter() {
        char[] password = "password123".toCharArray();
        LoginCommand cmd = new LoginCommand("john_doe", password, "192.168.1.1");
        
        char[] retrieved = cmd.passwordChars();
        retrieved[0] = 'X';
        
        // Original command password unchanged
        assertEquals('p', cmd.passwordChars()[0]);
    }

    @Test
    public void testAllFieldsRequired() {
        char[] password = "password123".toCharArray();
        
        // All fields set correctly
        LoginCommand cmd = new LoginCommand("john_doe", password, "192.168.1.1");
        assertNotNull(cmd.username());
        assertNotNull(cmd.passwordChars());
        assertNotNull(cmd.ipAddress());
    }

    @Test
    public void testMultipleInstancesHaveIndependentPasswords() {
        char[] pass1 = "password1".toCharArray();
        char[] pass2 = "password2".toCharArray();
        
        LoginCommand cmd1 = new LoginCommand("user1", pass1, "192.168.1.1");
        LoginCommand cmd2 = new LoginCommand("user2", pass2, "192.168.1.2");
        
        assertFalse(Arrays.equals(cmd1.passwordChars(), cmd2.passwordChars()));
    }

    @Test
    public void testEqualsWithIdenticalContent() {
        char[] password = "password123".toCharArray();
        LoginCommand cmd1 = new LoginCommand("john_doe", password, "192.168.1.1");
        LoginCommand cmd2 = new LoginCommand("john_doe", "password123".toCharArray(), "192.168.1.1");
        
        assertEquals(cmd1, cmd2);
    }

    @Test
    public void testEqualsWithDifferentUsername() {
        char[] password = "password123".toCharArray();
        LoginCommand cmd1 = new LoginCommand("john_doe", password, "192.168.1.1");
        LoginCommand cmd2 = new LoginCommand("jane_doe", "password123".toCharArray(), "192.168.1.1");
        
        assertNotEquals(cmd1, cmd2);
    }

    @Test
    public void testEqualsWithDifferentPassword() {
        LoginCommand cmd1 = new LoginCommand("john_doe", "password123".toCharArray(), "192.168.1.1");
        LoginCommand cmd2 = new LoginCommand("john_doe", "password456".toCharArray(), "192.168.1.1");
        
        assertNotEquals(cmd1, cmd2);
    }

    @Test
    public void testEqualsWithDifferentIpAddress() {
        char[] password = "password123".toCharArray();
        LoginCommand cmd1 = new LoginCommand("john_doe", password, "192.168.1.1");
        LoginCommand cmd2 = new LoginCommand("john_doe", "password123".toCharArray(), "192.168.1.2");
        
        assertNotEquals(cmd1, cmd2);
    }

    @Test
    public void testEqualsWithDifferentUserAgent() {
        // UserAgent field removed - test no longer applicable
    }

    @Test
    public void testEqualsWithSameInstance() {
        char[] password = "password123".toCharArray();
        LoginCommand cmd = new LoginCommand("john_doe", password, "192.168.1.1");
        
        assertEquals(cmd, cmd);
    }

    @Test
    public void testEqualsWithNull() {
        char[] password = "password123".toCharArray();
        LoginCommand cmd = new LoginCommand("john_doe", password, "192.168.1.1");
        
        assertNotEquals(cmd, null);
    }

    @Test
    public void testEqualsWithDifferentType() {
        char[] password = "password123".toCharArray();
        LoginCommand cmd = new LoginCommand("john_doe", password, "192.168.1.1");
        
        assertNotEquals(cmd, "string");
    }

    @Test
    public void testHashCodeWithIdenticalContent() {
        char[] password = "password123".toCharArray();
        LoginCommand cmd1 = new LoginCommand("john_doe", password, "192.168.1.1");
        LoginCommand cmd2 = new LoginCommand("john_doe", "password123".toCharArray(), "192.168.1.1");
        
        assertEquals(cmd1.hashCode(), cmd2.hashCode());
    }

    @Test
    public void testHashCodeWithDifferentPassword() {
        LoginCommand cmd1 = new LoginCommand("john_doe", "password123".toCharArray(), "192.168.1.1");
        LoginCommand cmd2 = new LoginCommand("john_doe", "password456".toCharArray(), "192.168.1.1");
        
        assertNotEquals(cmd1.hashCode(), cmd2.hashCode());
    }

    @Test
    public void testToStringMasksPassword() {
        char[] password = "password123".toCharArray();
        LoginCommand cmd = new LoginCommand("john_doe", password, "192.168.1.1");
        String cmdString = cmd.toString();
        
        assertTrue(cmdString.contains("username='john_doe'"));
        assertTrue(cmdString.contains("passwordChars=***"));
        assertTrue(cmdString.contains("ipAddress='192.168.1.1'"));
        assertFalse(cmdString.contains("password123"));
    }

    @Test
    public void testToStringFormat() {
        char[] password = "secret".toCharArray();
        LoginCommand cmd = new LoginCommand("admin", password, "10.0.0.1");
        String result = cmd.toString();
        
        assertEquals("LoginCommand{username='admin', passwordChars=***, ipAddress='10.0.0.1'}", result);
    }
}
