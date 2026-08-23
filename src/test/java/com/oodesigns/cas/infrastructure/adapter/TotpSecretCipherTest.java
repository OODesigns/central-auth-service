package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.KeyPassword;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(Arrays.equals(new byte[]{'C', 'A', 'S', 2}, Arrays.copyOf(ciphertext, 4)));
    }

    @Test
    void encryptUsesAUniqueNonce() {
        final byte[] first = TotpSecretCipher.encrypt(
            "JBSWY3DPEHPK3PXP", KeyPassword.of(TEST_KEY), new SecureRandom());
        final byte[] second = TotpSecretCipher.encrypt(
            "JBSWY3DPEHPK3PXP", KeyPassword.of(TEST_KEY), new SecureRandom());

        assertFalse(Arrays.equals(first, second));
    }

    @Test
    void decryptRejectsTampering() {
        final byte[] ciphertext = TotpSecretCipher.encrypt(
            "JBSWY3DPEHPK3PXP", KeyPassword.of(TEST_KEY), new SecureRandom());
        ciphertext[ciphertext.length - 1] ^= 1;

        assertThrows(IllegalStateException.class,
            () -> TotpSecretCipher.decrypt(ciphertext, KeyPassword.of(TEST_KEY)));
    }

    @Test
    void decryptRejectsUnknownTaggedVersion() {
        final byte[] ciphertext = TotpSecretCipher.encrypt(
            "JBSWY3DPEHPK3PXP", KeyPassword.of(TEST_KEY), new SecureRandom());
        ciphertext[3] = 3;

        assertThrows(IllegalArgumentException.class,
            () -> TotpSecretCipher.decrypt(ciphertext, KeyPassword.of(TEST_KEY)));
    }

    @Test
    void decryptRejectsTruncatedGcmEnvelope() {
        assertThrows(IllegalArgumentException.class,
            () -> TotpSecretCipher.decrypt(
                new byte[]{'C', 'A', 'S', 2, 0}, KeyPassword.of(TEST_KEY)));
    }

    @Test
    void decryptReadsLegacyCbcCiphertext() throws Exception {
        final byte[] legacyCiphertext = encryptLegacy("JBSWY3DPEHPK3PXP");

        assertEquals("JBSWY3DPEHPK3PXP",
            TotpSecretCipher.decrypt(legacyCiphertext, KeyPassword.of(TEST_KEY)));
    }

    @Test
    void decryptRejectsCorruptLegacyCiphertext() throws Exception {
        final byte[] legacyCiphertext = encryptLegacy("JBSWY3DPEHPK3PXP");
        legacyCiphertext[legacyCiphertext.length - 1] ^= 1;

        assertThrows(IllegalStateException.class,
            () -> TotpSecretCipher.decrypt(legacyCiphertext, KeyPassword.of(TEST_KEY)));
    }

    @Test
    void decryptRejectsShortCiphertext() {
        assertThrows(IllegalArgumentException.class,
            () -> TotpSecretCipher.decrypt(new byte[16], KeyPassword.of(TEST_KEY)));
    }

    @Test
    void encryptWrapsCipherCreationFailures() {
        try (MockedStatic<Cipher> mockedCipher = mockStatic(Cipher.class)) {
            mockedCipher.when(() -> Cipher.getInstance("AES/GCM/NoPadding"))
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

    private byte[] encryptLegacy(final String plaintext) throws Exception {
        final byte[] iv = new byte[16];
        Arrays.fill(iv, (byte) 7);
        final byte[] key = MessageDigest.getInstance("SHA-256")
            .digest(TEST_KEY.getBytes(StandardCharsets.UTF_8));
        final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        final byte[] payload = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.allocate(iv.length + payload.length).put(iv).put(payload).array();
    }
}
