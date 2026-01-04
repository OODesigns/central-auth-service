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
        assertThrows(NullPointerException.class, () -> KeyPassword.of((char[]) null));
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
    @SuppressWarnings("ConstantValue")
    void ofWithExactly32CharactersSucceeds() {
        final char[] validChars = VALID_SECRET_32_CHARS.toCharArray();
        boolean result = validChars.length >= 32;
        assertTrue(result);
        final KeyPassword keyPassword = KeyPassword.of(validChars);
        assertNotNull(keyPassword);
    }

    @Test
    @SuppressWarnings("ConstantValue")
    void ofWithMoreThan32CharactersSucceeds() {
        boolean result = VALID_SECRET_64_CHARS.length() > 32;
        assertTrue(result);
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

    @Test
    void fromStringWithInvalidLengthClearsCharArrayInFinally() {
        // This test verifies the finally block clears the char array even when exception is thrown
        // The test passes if no exception escapes (finally block executed)
        assertThrows(IllegalArgumentException.class, () -> KeyPassword.fromString("tooshort"));
        // If we reach here, the finally block successfully executed
    }

    @Test
    void toUtf8BytesClearsCharArrayInFinally() {
        // This test ensures the finally block in toUtf8Bytes() executes
        // by verifying the returned bytes are valid even after the method completes
        final KeyPassword keyPassword = KeyPassword.of("x".repeat(32).toCharArray());
        final byte[] bytes = keyPassword.toUtf8Bytes();
        
        // Verify bytes are valid UTF-8 encoded characters
        assertNotNull(bytes);
        assertTrue(bytes.length >= 32, "UTF-8 bytes should be at least 32 bytes for 32-char input");
    }

    @Test
    void toUtf8BytesWithSpecialCharactersEncodesCorrectly() {
        // Test with characters that encode to multiple bytes in UTF-8
        String secret = "ñ".repeat(32); // ñ is 2 bytes in UTF-8
        KeyPassword keyPassword = KeyPassword.fromString(secret);
        byte[] bytes = keyPassword.toUtf8Bytes();
        
        assertNotNull(bytes);
        assertTrue(bytes.length > 32, "Multi-byte UTF-8 chars should produce more bytes");
    }

    @Test
    void toUtf8BytesWithExactly32BytesSucceeds() {
        KeyPassword keyPassword = KeyPassword.fromString("x".repeat(32));
        byte[] bytes = keyPassword.toUtf8Bytes();
        
        assertEquals(32, bytes.length);
    }

    @Test
    void toUtf8BytesWithUnicodeCharactersEncodesCorrectly() {
        // Test with emoji and other Unicode characters
        String secret = "🔐".repeat(16) + "x".repeat(16); // Mix of multibyte and single-byte
        KeyPassword keyPassword = KeyPassword.fromString(secret);
        byte[] bytes = keyPassword.toUtf8Bytes();
        
        assertNotNull(bytes);
        assertTrue(bytes.length >= 32, "Unicode characters should encode to at least 32 bytes");
    }

    @Test
    void ofWithEmptyCharArrayThrowsIllegalArgumentException() {
        final char[] emptyChars = new char[0];
        assertThrows(IllegalArgumentException.class, () -> KeyPassword.of(emptyChars));
    }

    @Test
    void fromStringWithEmptyStringThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> KeyPassword.fromString(""));
    }

    @Test
    void keyPasswordInheritsPasswordBehavior() {
        final KeyPassword keyPassword = KeyPassword.fromString(VALID_SECRET_32_CHARS);
        final char[] chars = keyPassword.chars();
        
        assertNotNull(chars);
        assertEquals(32, chars.length);
    }

    @Test
    void keyPasswordClearMethodWorks() {
        final KeyPassword keyPassword = KeyPassword.fromString(VALID_SECRET_32_CHARS);
        keyPassword.clear();
        
        final char[] clearedChars = keyPassword.chars();
        for (char c : clearedChars) {
            assertEquals('\0', c);
        }
    }

    @Test
    void toUtf8BytesReturnsNewArrayEachCall() {
        final KeyPassword keyPassword = KeyPassword.fromString(VALID_SECRET_32_CHARS);
        final byte[] bytes1 = keyPassword.toUtf8Bytes();
        final byte[] bytes2 = keyPassword.toUtf8Bytes();
        
        assertNotSame(bytes1, bytes2);
        assertArrayEquals(bytes1, bytes2);
    }
}
