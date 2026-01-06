package com.oodesigns.cas.domain.value;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a secret key password suitable for HMAC signing.
 * Ensures the underlying secret meets minimum length requirements and
 * provides secure conversion to UTF-8 byte arrays.
 */
public class KeyPassword extends Password {
    private static final int MINIMUM_BYTES = 32;

    private KeyPassword(final char[] passwordChars) {
        super(passwordChars);
        if (passwordChars.length < MINIMUM_BYTES) {
            throw new IllegalArgumentException("Secret key must be at least 32 characters (256 bits) for HS256");
        }
    }

    /**
     * Create a KeyPassword from a char array.
     * Validates that the key meets minimum length requirements.
     *
     * @param passwordChars the secret key as char array
     * @return KeyPassword instance
     * @throws NullPointerException if passwordChars is null
     * @throws IllegalArgumentException if passwordChars is empty or insufficient length (< 32 characters)
     */
    public static KeyPassword of(final char[] passwordChars) {
        return new KeyPassword(passwordChars);
    }

    /**
     * Create a KeyPassword from a String.
     * Intended for testing; avoid using in production code where Strings cannot be cleared.
     *
     * @param secret secret key as String
     * @return KeyPassword instance
     * @throws NullPointerException if secret is null
     * @throws IllegalArgumentException if secret is empty or has insufficient length (< 32 characters)
     */
    public static KeyPassword of(final String secret) {
        Objects.requireNonNull(secret, "Secret key cannot be null");
        return KeyPassword.of(secret.toCharArray());
    }

    /**
     * Convert the internal password to UTF-8 bytes.
     * The returned array must be cleared by the caller after use.
     * Note: UTF-8 encoding guarantees at least 1 byte per character, so the
     * minimum 32-character requirement from of() ensures at least 32 bytes.
     *
     * @return UTF-8 encoded bytes representing the secret key
     */
    public byte[] toUtf8Bytes() {
        final char[] chars = chars();
        try {
            final CharBuffer charBuffer = CharBuffer.wrap(chars);
            final ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
            final byte[] secretKey = new byte[byteBuffer.remaining()];
            byteBuffer.get(secretKey);
            // StandardCharsets.UTF_8.encode() always returns a heap-backed buffer
            Arrays.fill(byteBuffer.array(), (byte) 0);
            return secretKey;
        } finally {
            Arrays.fill(chars, '\0');
        }
    }
}
