package com.oodesigns.cas.domain.value;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Value object representing an IP address.
 * Validates both IPv4 and IPv6 formats using Java's built-in validation.
 */
public record IpAddress(String value) {

    public IpAddress {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("IP address cannot be null or blank");
        }
        if (!isValidIpAddress(value)) {
            throw new IllegalArgumentException("Invalid IP address format: " + value);
        }
    }

    /**
     * Check if the given string is a valid IPv4 or IPv6 address.
     * Uses Java's InetAddress for standard validation.
     * 
     * @param ip the IP address to validate
     * @return true if valid IPv4 or IPv6, false otherwise
     */
    private static boolean isValidIpAddress(final String ip) {
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (final UnknownHostException e) {
            return false;
        }
    }

    public static IpAddress of(final String value) {
        return new IpAddress(value);
    }

    public String asString() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
