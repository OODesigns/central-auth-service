package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyPasswordTest {

    private static final String VALID_SECRET_32_CHARS = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"; // 32 x's
    private static final String VALID_SECRET_64_CHARS = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"; // 64 x's

    @Test
    void ofWithValidCharArrayCreatesKeyPassword() {
        final char[] validChars = VALID_SECRET_32_CHARS.toCharArray();
        final KeyPassword keyPassword = KeyPassword.of(validChars);
        assertNotNull(keyPassword);
    }

    @Test
    void ofWithNullCharArrayThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> KeyPassword.of(null));
    }

    @Test
    void ofWithInsufficientLengthThrowsIllegalArgumentException() {
        final char[] shortChars = "short".toCharArray();
        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> KeyPassword.of(shortChars)
        );
        assertTrue(ex.getMessage().contains("32 characters"));
        assertTrue(ex.getMessage().contains("256 bits"));
    }

    @Test
    void ofWithExactly32CharactersSucceeds() {
        final char[] validChars = VALID_SECRET_32_CHARS.toCharArray();
        assertTrue(validChars.length >= 32);
        final KeyPassword keyPassword = KeyPassword.of(validChars);
        assertNotNull(keyPassword);
    }

    @Test
    void ofWithMoreThan32CharactersSucceeds() {
        assertTrue(VALID_SECRET_64_CHARS.length() > 32);
        final char[] validChars = VALID_SECRET_64_CHARS.toCharArray();
        final KeyPassword keyPassword = KeyPassword.of(validChars);
        assertNotNull(keyPassword);
    }

    @Test
    void fromStringWithValidSecretCreatesKeyPassword() {
        final KeyPassword keyPassword = KeyPassword.fromString(VALID_SECRET_32_CHARS);
        assertNotNull(keyPassword);
    }

    @Test
    void fromStringWithNullThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> KeyPassword.fromString(null));
    }

    @Test
    void fromStringWithInsufficientLengthThrowsIllegalArgumentException() {
        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> KeyPassword.fromString("short")
        );
        assertTrue(ex.getMessage().contains("32 characters"));
    }

    @Test
    void toUtf8BytesConvertsPasswordToBytes() {
        final KeyPassword keyPassword = KeyPassword.fromString(VALID_SECRET_32_CHARS);
        final byte[] utf8Bytes = keyPassword.toUtf8Bytes();
        assertNotNull(utf8Bytes);
        assertTrue(utf8Bytes.length >= 32, "UTF-8 encoded bytes should be at least 32 bytes");
    }

    @Test
    void toUtf8BytesWithLongerSecretProducesMoreBytes() {
        final KeyPassword keyPassword32 = KeyPassword.fromString(VALID_SECRET_32_CHARS);
        final KeyPassword keyPassword64 = KeyPassword.fromString(VALID_SECRET_64_CHARS);
        
        final byte[] bytes32 = keyPassword32.toUtf8Bytes();
        final byte[] bytes64 = keyPassword64.toUtf8Bytes();
        
        assertTrue(bytes64.length > bytes32.length);
    }

    @Test
    void multipleCallsToToUtf8BytesProduceIdenticalResults() {
        final KeyPassword keyPassword = KeyPassword.fromString(VALID_SECRET_32_CHARS);
        final byte[] bytes1 = keyPassword.toUtf8Bytes();
        final byte[] bytes2 = keyPassword.toUtf8Bytes();
        
        assertArrayEquals(bytes1, bytes2);
    }
}
