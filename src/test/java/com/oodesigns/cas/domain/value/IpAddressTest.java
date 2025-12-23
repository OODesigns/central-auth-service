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
        IpAddress ipv4 = IpAddress.of("192.168.1.1");
        assertEquals("192.168.1.1", ipv4.asString());
    }

    @Test
    void testValidIPv4Localhost() {
        IpAddress localhost = IpAddress.of("127.0.0.1");
        assertEquals("127.0.0.1", localhost.asString());
    }

    @Test
    void testValidIPv4Broadcast() {
        IpAddress broadcast = IpAddress.of("255.255.255.255");
        assertEquals("255.255.255.255", broadcast.asString());
    }

    @Test
    void testValidIPv6() {
        IpAddress ipv6 = IpAddress.of("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        assertEquals("2001:0db8:85a3:0000:0000:8a2e:0370:7334", ipv6.asString());
    }

    @Test
    void testValidIPv6Shortened() {
        IpAddress ipv6 = IpAddress.of("::1");
        assertEquals("::1", ipv6.asString());
    }

    @Test
    void testValidIPv6Localhost() {
        IpAddress ipv6Localhost = IpAddress.of("::1");
        assertEquals("::1", ipv6Localhost.asString());
    }

    @Test
    void testInvalidIPv4() {
        assertThrows(IllegalArgumentException.class, () -> IpAddress.of("999.999.999.999"));
    }

    @Test
    void testInvalidIPv4Format() {
        assertThrows(IllegalArgumentException.class, () -> IpAddress.of("192.168.1"));
    }

    @Test
    void testInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> IpAddress.of("not-an-ip"));
    }

    @Test
    void testNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> IpAddress.of(null));
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
        IpAddress ip1 = IpAddress.of("192.168.1.1");
        IpAddress ip2 = IpAddress.of("192.168.1.1");
        assertEquals(ip1, ip2);
    }

    @Test
    void testInequality() {
        IpAddress ip1 = IpAddress.of("192.168.1.1");
        IpAddress ip2 = IpAddress.of("192.168.1.2");
        assertNotEquals(ip1, ip2);
    }

    @Test
    void testHashCodeConsistency() {
        IpAddress ip1 = IpAddress.of("192.168.1.1");
        IpAddress ip2 = IpAddress.of("192.168.1.1");
        assertEquals(ip1.hashCode(), ip2.hashCode());
    }

    @Test
    void testToString() {
        IpAddress ip = IpAddress.of("192.168.1.1");
        assertEquals("192.168.1.1", ip.toString());
    }
}
