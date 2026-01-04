package com.oodesigns.cas.domain.value;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Value object representing an IP address.
 * Validates both IPv4 and IPv6 formats using Java's built-in validation.
 */
public final class IpAddress extends ValidatedValue<String, String> {

    public IpAddress(final String value) {
        super(value);
    }

    public static IpAddress of(final String value) {
        return new IpAddress(value);
    }

    @Override
    protected String parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("IP address cannot be null or blank");
        }
        return raw;
    }

    @Override
    protected String validate(final String value) {
        if (!isValidIpAddress(value)) {
            throw new IllegalArgumentException(String.format("Invalid IP address format: %s", value));
        }
        return value;
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
}
