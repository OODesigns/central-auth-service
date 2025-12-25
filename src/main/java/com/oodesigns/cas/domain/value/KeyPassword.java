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
public final class KeyPassword extends Password {
    private static final int MINIMUM_BYTES = 32;

    private KeyPassword(final char[] passwordChars) {
        super(passwordChars);
    }

    /**
     * Create a KeyPassword from a char array, enforcing minimum length requirements.
     * The provided array is not retained; callers should clear it after invocation.
     *
     * @param passwordChars secret key characters
     * @return validated KeyPassword instance
     * @throws IllegalArgumentException if characters are null or insufficient length
     */
    public static KeyPassword of(final char[] passwordChars) {
        Objects.requireNonNull(passwordChars, "Secret key cannot be null");
        if (passwordChars.length < MINIMUM_BYTES) {
            throw new IllegalArgumentException("Secret key must be at least 32 characters (256 bits) for HS256");
        }
        return new KeyPassword(passwordChars);
    }

    /**
     * Convenience factory for creating a KeyPassword from a String.
     * Intended for testing; avoid using in production code where Strings cannot be cleared.
     *
     * @param secret secret key as String
     * @return KeyPassword instance
     */
    public static KeyPassword fromString(final String secret) {
        Objects.requireNonNull(secret, "Secret key cannot be null");
        final char[] chars = secret.toCharArray();
        try {
            return of(chars);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    /**
     * Convert the internal password to UTF-8 bytes.
     * The returned array must be cleared by the caller after use.
     *
     * @return UTF-8 encoded bytes representing the secret key
     * @throws IllegalStateException if the resulting byte array is insufficient length
     */
    public byte[] toUtf8Bytes() {
        final char[] chars = chars();
        try {
            final CharBuffer charBuffer = CharBuffer.wrap(chars);
            final ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
            final byte[] secretKey = new byte[byteBuffer.remaining()];
            byteBuffer.get(secretKey);
            if (byteBuffer.hasArray()) {
                Arrays.fill(byteBuffer.array(), (byte) 0);
            }
            if (secretKey.length < MINIMUM_BYTES) {
                Arrays.fill(secretKey, (byte) 0);
                throw new IllegalStateException("Secret key must be at least 32 bytes for HS256");
            }
            return secretKey;
        } finally {
            Arrays.fill(chars, '\0');
        }
    }
}
