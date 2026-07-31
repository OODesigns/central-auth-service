package com.oodesigns.cas.domain.value;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

import com.oodesigns.cas.util.validation.ValidatedValue;

/**
 * Value object representing an IP address.
 * Validates both IPv4 and IPv6 formats using Java's built-in validation.
 * <p>
 * Validation happens in the static factory method before construction.
 *
 */
public final class IpAddress extends ValidatedValue<String> {

    /**
     * Create an IP address value object.
     * Assumes the value has already been validated.
     *
     * @param value the validated IP address string
     */
    private IpAddress(final String value) {
        super(value);
    }

    /**
     * Factory method to create an IP address.
     * Performs all validation before construction.
     * 
     * @param value the IP address string
     * @return IpAddress instance
     * @throws NullPointerException if value is null
     * @throws IllegalArgumentException if value is blank or invalid format
     */
    public static IpAddress of(final String value) {
        Objects.requireNonNull(value, "IP address cannot be null");
        validateIpAddress(value);  // Perform validation
        return new IpAddress(value);
    }

    /**
     * Validate that the given string is a valid IPv4 or IPv6 address.
     * 
     * @param value the IP address to validate
     * @throws IllegalArgumentException if invalid
     */
    private static void validateIpAddress(final String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("IP address cannot be blank");
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
}
