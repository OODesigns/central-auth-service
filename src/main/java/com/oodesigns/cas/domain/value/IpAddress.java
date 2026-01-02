package com.oodesigns.cas.domain.value;

import java.net.InetAddress;
import java.net.UnknownHostException;

import jakarta.annotation.Nonnull;

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
            throw new IllegalArgumentException(String.format("Invalid IP address format: %s", value));
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
            //noinspection ResultOfMethodCallIgnored
            InetAddress.getByName(ip);
            return true;
        } catch (final UnknownHostException _) {
            return false;
        }
    }

    public static IpAddress of(final String value) {
        return new IpAddress(value);
    }

    @Nonnull
    public String asString() {
        return value;
    }

    @Nonnull
    @Override
    public String toString() {
        return value;
    }
}
