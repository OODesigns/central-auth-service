package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.value.IpAddress;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.Username;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LoginCommand application command.
 * Validates: value object validation, security, immutability.
 */
class LoginCommandTest {

    @Test
    void testValidCommand() {
        Username username = Username.of("john_doe");
        Password password = new Password("password123".toCharArray());
        IpAddress ipAddress = IpAddress.of("192.168.1.1");
        LoginCommand cmd = new LoginCommand(username, password, ipAddress);

        assertEquals(username, cmd.username());
        assertEquals(ipAddress, cmd.ipAddress());
        assertEquals(password, cmd.password());
    }

    @Test
    void testNullUsernameThrows() {
        Password password = new Password("password123".toCharArray());
        IpAddress ipAddress = IpAddress.of("192.168.1.1");
        assertThrows(NullPointerException.class,
            () -> new LoginCommand(null, password, ipAddress));
    }

    @Test
    void testNullPasswordThrows() {
        Username username = Username.of("john_doe");
        IpAddress ipAddress = IpAddress.of("192.168.1.1");
        assertThrows(NullPointerException.class,
            () -> new LoginCommand(username, null, ipAddress));
    }

    @Test
    void testNullIpAddressThrows() {
        Username username = Username.of("john_doe");
        Password password = new Password("password123".toCharArray());
        assertThrows(NullPointerException.class,
            () -> new LoginCommand(username, password, null));
    }

    @Test
    void testInvalidUsernameFormatThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> Username.of("ab"));  // Too short
        assertThrows(IllegalArgumentException.class,
            () -> Username.of("user@invalid#chars"));  // Invalid characters
    }

    @Test
    void testInvalidIpAddressThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> IpAddress.of("999.999.999.999"));  // Invalid IPv4
        assertThrows(IllegalArgumentException.class,
            () -> IpAddress.of("not-an-ip"));  // Invalid format
    }

    @Test
    void testEmptyPasswordThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Password(new char[0]));
    }

    @Test
    void testNullPasswordCharArrayThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Password(null));
    }

    @Test
    void testPasswordCharArrayCloned() {
        char[] original = "password123".toCharArray();
        Password password = new Password(original);
        
        original[0] = 'X';
        
        assertEquals('p', password.chars()[0]);
    }

    @Test
    void testPasswordCharArrayNotMutableViaGetter() {
        Password password = new Password("password123".toCharArray());
        
        char[] retrieved = password.chars();
        retrieved[0] = 'X';
        
        assertEquals('p', password.chars()[0]);
    }

    @Test
    void testPasswordClear() {
        char[] passwordChars = "secret".toCharArray();
        Password password = new Password(passwordChars);
        
        password.clear();
        
        for (char c : password.chars()) {
            assertEquals('\0', c);
        }
    }

    @Test
    void testEqualsWithSameInstance() {
        Username username = Username.of("john_doe");
        Password password = new Password("password123".toCharArray());
        IpAddress ipAddress = IpAddress.of("192.168.1.1");
        LoginCommand cmd1 = new LoginCommand(username, password, ipAddress);
        LoginCommand cmd2 = new LoginCommand(username, password, ipAddress);
        
        assertEquals(cmd1, cmd2);
    }

    @Test
    void testEqualsWithNull() {
        Username username = Username.of("john_doe");
        Password password = new Password("password123".toCharArray());
        IpAddress ipAddress = IpAddress.of("192.168.1.1");
        LoginCommand cmd = new LoginCommand(username, password, ipAddress);
        
        assertNotEquals(null, cmd);
    }

    @Test
    void testEqualsWithDifferentType() {
        Username username = Username.of("john_doe");
        Password password = new Password("password123".toCharArray());
        IpAddress ipAddress = IpAddress.of("192.168.1.1");
        LoginCommand cmd = new LoginCommand(username, password, ipAddress);
        LoginCommand differentCmd = new LoginCommand(Username.of("jane_doe"), password, ipAddress);
        
        assertNotEquals(differentCmd, cmd);
    }

    @Test
    void testToStringMasksPassword() {
        Username username = Username.of("john_doe");
        Password password = new Password("password123".toCharArray());
        IpAddress ipAddress = IpAddress.of("192.168.1.1");
        LoginCommand cmd = new LoginCommand(username, password, ipAddress);
        
        String cmdString = cmd.toString();
        
        // Record's toString delegates to field's toString()
        // Password.toString() returns "Password{***}"
        assertTrue(cmdString.contains("Password{***}"));
        assertFalse(cmdString.contains("password123"));
    }

    @Test
    void testValidIPv6Address() {
        IpAddress ipv6 = IpAddress.of("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        assertEquals("2001:0db8:85a3:0000:0000:8a2e:0370:7334", ipv6.value());
    }

    @Test
    void testUsernameNormalized() {
        Username username1 = Username.of("John_Doe");
        Username username2 = Username.of("john_doe");
        
        assertEquals(username1, username2);
    }
}
