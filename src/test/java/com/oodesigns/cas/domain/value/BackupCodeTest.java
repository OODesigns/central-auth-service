package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BackupCode Value Object Tests")
class BackupCodeTest {

    private static final String VALID_CODE = "ABCD-EFGH-IJKL-MNOP";
    private static final String VALID_CODE_NUMERIC = "1234-5678-9012-3456";
    private static final String INVALID_CODE_LOWERCASE = "abcd-efgh-ijkl-mnop";
    private static final String INVALID_CODE_MISSING_DASH = "ABCDEFGHIJKLMNOP";
    private static final String INVALID_CODE_WRONG_LENGTH = "ABCD-EFGH-IJKL";
    private static final String INVALID_CODE_SYMBOLS = "ABCD-EFGH-IJKL-MN@P";

    @Test
    @DisplayName("Valid backup code creates BackupCode")
    void testValidBackupCode() {
        final BackupCode code = BackupCode.of(VALID_CODE);
        assertEquals(VALID_CODE, code.value());
        assertEquals(19, code.length());
    }

    @Test
    @DisplayName("Valid numeric backup code creates BackupCode")
    void testValidNumericBackupCode() {
        final BackupCode code = BackupCode.of(VALID_CODE_NUMERIC);
        assertEquals(VALID_CODE_NUMERIC, code.value());
    }

    @Test
    @DisplayName("Null code throws NullPointerException")
    void testNullCodeThrows() {
        assertThrows(NullPointerException.class, () -> BackupCode.of(null));
    }

    @Test
    @DisplayName("Empty string throws IllegalArgumentException")
    void testEmptyCodeThrows() {
        assertThrows(IllegalArgumentException.class, () -> BackupCode.of(""));
    }

    @Test
    @DisplayName("Lowercase characters throw IllegalArgumentException")
    void testLowercaseThrows() {
        assertThrows(IllegalArgumentException.class, () -> BackupCode.of(INVALID_CODE_LOWERCASE));
    }

    @Test
    @DisplayName("Code without dashes throws IllegalArgumentException")
    void testMissingDashesThrows() {
        assertThrows(IllegalArgumentException.class, () -> BackupCode.of(INVALID_CODE_MISSING_DASH));
    }

    @Test
    @DisplayName("Code with wrong length throws IllegalArgumentException")
    void testWrongLengthThrows() {
        assertThrows(IllegalArgumentException.class, () -> BackupCode.of(INVALID_CODE_WRONG_LENGTH));
    }

    @Test
    @DisplayName("Code with invalid characters throws IllegalArgumentException")
    void testInvalidCharactersThrow() {
        assertThrows(IllegalArgumentException.class, () -> BackupCode.of(INVALID_CODE_SYMBOLS));
    }

    @Test
    @DisplayName("getCode() returns plaintext code")
    void testGetCode() {
        final BackupCode code = BackupCode.of(VALID_CODE);
        assertEquals(VALID_CODE, code.getCode());
    }

    @Test
    @DisplayName("normalized() returns code without dashes")
    void testNormalized() {
        final BackupCode code = BackupCode.of(VALID_CODE);
        assertEquals("ABCDEFGHIJKLMNOP", code.normalized());
    }

    @Test
    @DisplayName("Two codes with same value are equal")
    void testEqualCodes() {
        final BackupCode code1 = BackupCode.of(VALID_CODE);
        final BackupCode code2 = BackupCode.of(VALID_CODE);
        assertEquals(code1, code2);
    }

    @Test
    @DisplayName("Two codes with different values are not equal")
    void testNotEqualCodes() {
        final BackupCode code1 = BackupCode.of(VALID_CODE);
        final BackupCode code2 = BackupCode.of(VALID_CODE_NUMERIC);
        assertNotEquals(code1, code2);
    }

    @Test
    @DisplayName("Code is immutable")
    void testCodeImmutable() {
        final BackupCode code = BackupCode.of(VALID_CODE);
        final String value = code.value();
        assertEquals(VALID_CODE, value);
    }
}

