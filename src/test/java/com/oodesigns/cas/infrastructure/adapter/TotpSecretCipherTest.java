package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.KeyPassword;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class TotpSecretCipherTest {

    private static final String TEST_KEY = "0123456789ABCDEF0123456789ABCDEF";

    @Test
    void encryptAndDecryptRoundTripsPlaintext() {
        final KeyPassword keyPassword = KeyPassword.of(TEST_KEY);
        final byte[] ciphertext = TotpSecretCipher.encrypt("JBSWY3DPEHPK3PXP", keyPassword, new SecureRandom());

        final String plaintext = TotpSecretCipher.decrypt(ciphertext, KeyPassword.of(TEST_KEY));

        assertEquals("JBSWY3DPEHPK3PXP", plaintext);
        assertTrue(ciphertext.length > plaintext.length());
    }

    @Test
    void decryptRejectsShortCiphertext() {
        assertThrows(IllegalArgumentException.class,
            () -> TotpSecretCipher.decrypt(new byte[16], KeyPassword.of(TEST_KEY)));
    }

    @Test
    void encryptWrapsCipherCreationFailures() {
        try (MockedStatic<Cipher> mockedCipher = mockStatic(Cipher.class)) {
            mockedCipher.when(() -> Cipher.getInstance("AES/CBC/PKCS5Padding"))
                .thenThrow(new NoSuchAlgorithmException("boom"));

            assertThrows(IllegalStateException.class,
                () -> TotpSecretCipher.encrypt("JBSWY3DPEHPK3PXP", KeyPassword.of(TEST_KEY), new SecureRandom()));
        }
    }

    @Test
    void encryptWrapsMissingSha256Digest() {
        try (MockedStatic<MessageDigest> mockedDigest = mockStatic(MessageDigest.class)) {
            mockedDigest.when(() -> MessageDigest.getInstance("SHA-256"))
                .thenThrow(new NoSuchAlgorithmException("boom"));

            assertThrows(IllegalStateException.class,
                () -> TotpSecretCipher.encrypt("JBSWY3DPEHPK3PXP", KeyPassword.of(TEST_KEY), new SecureRandom()));
        }
    }
}
