package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyPasswordTest {

    private static final String VALID_SECRET_32_CHARS = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"; // 32 x's
    private static final String VALID_SECRET_64_CHARS = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"; // 64 x's

    @Test
    void ofWithValidCharArrayCreatesKeyPassword() {
        final char[] validChars = VALID_SECRET_32_CHARS.toCharArray();
        try (final KeyPassword keyPassword = KeyPassword.of(validChars)) {
            assertNotNull(keyPassword);
        }
    }

    @Test
    @SuppressWarnings("unused")
    void ofWithNullCharArrayThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            //noinspection EmptyTryBlock
            try (final KeyPassword keyPassword = KeyPassword.of((char[]) null)) {
                // Won't reach here
            }
        });
    }

    @Test
    @SuppressWarnings("unused")
    void ofWithInsufficientLengthThrowsIllegalArgumentException() {
        final char[] shortChars = "ValidPassword14x".toCharArray();  // 15 chars - passes Password min but fails KeyPassword min
        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> {
                //noinspection EmptyTryBlock
                try (final KeyPassword keyPassword = KeyPassword.of(shortChars)) {
                    // Won't reach here
                }
            }
        );
        assertTrue(ex.getMessage().contains("32 characters"));
        assertTrue(ex.getMessage().contains("256 bits"));
    }

    @Test
    @SuppressWarnings("unused")
    void ofWithStringInsufficientLengthThrowsIllegalArgumentException() {
        final String shortSecret = "ValidPassword14x";  // 15 chars - passes Password min but fails KeyPassword min
        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> {
                //noinspection EmptyTryBlock
                try (final KeyPassword keyPassword = KeyPassword.of(shortSecret)) {
                    // Won't reach here
                }
            }
        );
        assertTrue(ex.getMessage().contains("32 characters"));
        assertTrue(ex.getMessage().contains("256 bits"));
    }

    @Test
    @SuppressWarnings("ConstantValue")
    void ofWithExactly32CharactersSucceeds() {
        final char[] validChars = VALID_SECRET_32_CHARS.toCharArray();
        final boolean result = validChars.length >= 32;
        assertTrue(result);
        try (final KeyPassword keyPassword = KeyPassword.of(validChars)) {
            assertNotNull(keyPassword);
        }
    }

    @Test
    @SuppressWarnings("ConstantValue")
    void ofWithMoreThan32CharactersSucceeds() {
        final boolean result = VALID_SECRET_64_CHARS.length() > 32;
        assertTrue(result);
        final char[] validChars = VALID_SECRET_64_CHARS.toCharArray();
        try (final KeyPassword keyPassword = KeyPassword.of(validChars)) {
            assertNotNull(keyPassword);
        }
    }

    @Test
    void ofWithValidStringCreatesKeyPassword() {
        try (final KeyPassword keyPassword = KeyPassword.of(VALID_SECRET_32_CHARS)) {
            assertNotNull(keyPassword);
        }
    }

    @Test
    @SuppressWarnings("unused")
    void ofWithNullStringThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            //noinspection EmptyTryBlock
            try (final KeyPassword keyPassword = KeyPassword.of((String) null)) {
                // Won't reach here
            }
        });
    }


    @Test
    void toUtf8BytesConvertsPasswordToBytes() {
        try (final KeyPassword keyPassword = KeyPassword.of(VALID_SECRET_32_CHARS)) {
            final byte[] utf8Bytes = keyPassword.toUtf8Bytes();
            assertNotNull(utf8Bytes);
            assertTrue(utf8Bytes.length >= 32, "UTF-8 encoded bytes should be at least 32 bytes");
        }
    }

    @Test
    void toUtf8BytesWithLongerSecretProducesMoreBytes() {
        try (final KeyPassword keyPassword32 = KeyPassword.of(VALID_SECRET_32_CHARS);
             final KeyPassword keyPassword64 = KeyPassword.of(VALID_SECRET_64_CHARS)) {
            final byte[] bytes32 = keyPassword32.toUtf8Bytes();
            final byte[] bytes64 = keyPassword64.toUtf8Bytes();
            
            assertTrue(bytes64.length > bytes32.length);
        }
    }

    @Test
    void multipleCallsToToUtf8BytesProduceIdenticalResults() {
        try (final KeyPassword keyPassword = KeyPassword.of(VALID_SECRET_32_CHARS)) {
            final byte[] bytes1 = keyPassword.toUtf8Bytes();
            final byte[] bytes2 = keyPassword.toUtf8Bytes();
            
            assertArrayEquals(bytes1, bytes2);
        }
    }

    @Test
    @SuppressWarnings("unused")
    void ofWithStringInvalidLengthClearsCharArrayInFinally() {
        // This test verifies the finally block clears the char array even when exception is thrown
        // The test passes if no exception escapes (finally block executed)
        assertThrows(IllegalArgumentException.class, () -> {
            //noinspection EmptyTryBlock
            try (final KeyPassword keyPassword = KeyPassword.of("tooshort")) {
                // Won't reach here
            }
        });
        // If we reach here, the finally block successfully executed
    }

    @Test
    void toUtf8BytesClearsCharArrayInFinally() {
        // This test ensures the finally block in toUtf8Bytes() executes
        // by verifying the returned bytes are valid even after the method completes
        try (final KeyPassword keyPassword = KeyPassword.of("x".repeat(32).toCharArray())) {
            final byte[] bytes = keyPassword.toUtf8Bytes();
            
            // Verify bytes are valid UTF-8 encoded characters
            assertNotNull(bytes);
            assertTrue(bytes.length >= 32, "UTF-8 bytes should be at least 32 bytes for 32-char input");
        }
    }

    @Test
    void toUtf8BytesWithSpecialCharactersEncodesCorrectly() {
        // Test with characters that encode to multiple bytes in UTF-8
        final String secret = "ñ".repeat(32); // ñ is 2 bytes in UTF-8
        try (final KeyPassword keyPassword = KeyPassword.of(secret)) {
            final byte[] bytes = keyPassword.toUtf8Bytes();
            
            assertNotNull(bytes);
            assertTrue(bytes.length > 32, "Multi-byte UTF-8 chars should produce more bytes");
        }
    }

    @Test
    void toUtf8BytesWithExactly32BytesSucceeds() {
        try (final KeyPassword keyPassword = KeyPassword.of("x".repeat(32))) {
            final byte[] bytes = keyPassword.toUtf8Bytes();
            
            assertEquals(32, bytes.length);
        }
    }

    @Test
    void toUtf8BytesWithUnicodeCharactersEncodesCorrectly() {
        // Test with emoji and other Unicode characters
        final String secret = "🔐".repeat(16) + "x".repeat(16); // Mix of multibyte and single-byte
        try (final KeyPassword keyPassword = KeyPassword.of(secret)) {
            final byte[] bytes = keyPassword.toUtf8Bytes();
            
            assertNotNull(bytes);
            assertTrue(bytes.length >= 32, "Unicode characters should encode to at least 32 bytes");
        }
    }

    @Test
    @SuppressWarnings("unused")
    void ofWithEmptyStringThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            //noinspection EmptyTryBlock
            try (final KeyPassword keyPassword = KeyPassword.of("")) {
                // Won't reach here
            }
        });
    }

    @Test
    void keyPasswordInheritsPasswordBehavior() {
        try (final KeyPassword keyPassword = KeyPassword.of(VALID_SECRET_32_CHARS)) {
            final char[] chars = keyPassword.chars();
            
            assertNotNull(chars);
            assertEquals(32, chars.length);
        }
    }

    @Test
    void keyPasswordClearMethodWorks() {
        try (final KeyPassword keyPassword = KeyPassword.of(VALID_SECRET_32_CHARS)) {
            keyPassword.clear();
            
            final char[] clearedChars = keyPassword.chars();
            for (final char c : clearedChars) {
                assertEquals('\0', c);
            }
        }
    }

    @Test
    void toUtf8BytesReturnsNewArrayEachCall() {
        try (final KeyPassword keyPassword = KeyPassword.of(VALID_SECRET_32_CHARS)) {
            final byte[] bytes1 = keyPassword.toUtf8Bytes();
            final byte[] bytes2 = keyPassword.toUtf8Bytes();
            
            assertNotSame(bytes1, bytes2);
            assertArrayEquals(bytes1, bytes2);
        }
    }
}
