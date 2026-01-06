package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IpAddress value object.
 * Validates: IPv4, IPv6, null/blank handling.
 */
class IpAddressTest {

    @Test
    void testValidIPv4() {
        final IpAddress ipv4 = IpAddress.of("192.168.1.1");
        assertEquals("192.168.1.1", ipv4.value());
    }

    @Test
    void testValidIPv4Localhost() {
        final IpAddress localhost = IpAddress.of("127.0.0.1");
        assertEquals("127.0.0.1", localhost.value());
    }

    @Test
    void testValidIPv4Broadcast() {
        final IpAddress broadcast = IpAddress.of("255.255.255.255");
        assertEquals("255.255.255.255", broadcast.value());
    }

    @Test
    void testValidIPv6() {
        final IpAddress ipv6 = IpAddress.of("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        assertEquals("2001:0db8:85a3:0000:0000:8a2e:0370:7334", ipv6.value());
    }

    @Test
    void testValidIPv6Shortened() {
        final IpAddress ipv6 = IpAddress.of("::1");
        assertEquals("::1", ipv6.value());
    }

    @Test
    void testValidIPv6Localhost() {
        final IpAddress ipv6Localhost = IpAddress.of("::1");
        assertEquals("::1", ipv6Localhost.value());
    }

    @Test
    void testInvalidIPv4() {
        assertThrows(IllegalArgumentException.class, () -> IpAddress.of("999.999.999.999"));
    }

    @Test
    void testInvalidIPv4Format() {
        // InetAddress rejects strings with spaces, pipes, or other invalid characters
        assertThrows(IllegalArgumentException.class, () -> IpAddress.of("192.168.1.1 "));
    }

    @Test
    void testInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> IpAddress.of("not-an-ip"));
    }

    @Test
    void testNullThrows() {
        assertThrows(NullPointerException.class, () -> IpAddress.of(null));
    }

    @Test
    void testBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> IpAddress.of("   "));
    }

    @Test
    void testEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> IpAddress.of(""));
    }

    @Test
    void testEquality() {
        final IpAddress ip1 = IpAddress.of("192.168.1.1");
        final IpAddress ip2 = IpAddress.of("192.168.1.1");
        assertEquals(ip1, ip2);
    }

    @Test
    void testInequality() {
        final IpAddress ip1 = IpAddress.of("192.168.1.1");
        final IpAddress ip2 = IpAddress.of("192.168.1.2");
        assertNotEquals(ip1, ip2);
    }

    @Test
    void testHashCodeConsistency() {
        final IpAddress ip1 = IpAddress.of("192.168.1.1");
        final IpAddress ip2 = IpAddress.of("192.168.1.1");
        assertEquals(ip1.hashCode(), ip2.hashCode());
    }

    @Test
    void testToString() {
        final IpAddress ip = IpAddress.of("192.168.1.1");
        assertEquals("192.168.1.1", ip.toString());
    }
}
